package com.tencent.dataflow.domain.service.impl;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.TaskExecutor;
import com.tencent.dataflow.domain.node.ControlPolicy;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.PolicyRule;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.ControlPlaneService;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneServiceImpl implements ControlPlaneService {

    private final NodeRepository nodeRepository;
    private final TaskExecutor taskExecutor;
    // In a real app, this would be a service to look up schemas
    private final Map<String, TaskSchema> schemaRegistry; 

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public void onEvent(Event event) {
        log.info("Received event: {}", event.getType());
        // 1. Find affected nodes (Simplified: find all for demo)
        List<Node> nodes = nodeRepository.findAllActiveNodes();
        
        for (Node node : nodes) {
            try {
                // 1. Evaluate Control Policy (Running nodes)
                evaluateNodePolicy(node, event);
                
                // 2. Evaluate Start Condition (Waiting nodes)
                evaluateStartCondition(node, event);
            } catch (Exception e) {
                log.error("Failed to evaluate policy for node {}", node.getId(), e);
            }
        }
    }

    private void evaluateStartCondition(Node node, Event event) {
        String startWhen = node.getStartWhen();
        if (startWhen == null || startWhen.isBlank()) return;

        StandardEvaluationContext context = createEvaluationContext(node, event);

        if (evaluate(startWhen, context)) {
            Map<String, Object> params = resolveParams(node.getStartPayload(), context);
            executeAction(node, ActionDefinition.ACTION_START, params);
        }
    }

    private StandardEvaluationContext createEvaluationContext(Node node, Event event) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Map<String, Object> root = new HashMap<>();
        root.put("event", event);
        root.put("node", node);
        context.setRootObject(root);
        context.setVariable("event", event);
        context.setVariable("node", node);
        return context;
    }

    @Override
    public void evaluateNodePolicy(Node node, Event event) {
        ControlPolicy policy = node.getControlPolicy();
        if (policy == null) return;

        StandardEvaluationContext context = createEvaluationContext(node, event);

        // 1. Evaluate Standard Policies
        if (evaluate(policy.getStopWhen(), context)) {
            executeAction(node, ActionDefinition.ACTION_STOP, null);
        }
        if (evaluate(policy.getRestartWhen(), context)) {
            executeAction(node, ActionDefinition.ACTION_RESTART, null);
        }
        if (evaluate(policy.getRetryWhen(), context)) {
            executeAction(node, ActionDefinition.ACTION_RETRY, null);
        }

        // 2. Evaluate Custom Rules
        if (policy.getCustomRules() != null) {
            for (PolicyRule rule : policy.getCustomRules()) {
                if (evaluate(rule.getCondition(), context)) {
                    Map<String, Object> params = resolveParams(rule.getActionParams(), context);
                    executeAction(node, rule.getAction(), params);
                }
            }
        }
    }

    private boolean evaluate(String expressionStr, StandardEvaluationContext context) {
        if (expressionStr == null || expressionStr.isBlank()) return false;
        try {
            Expression exp = parser.parseExpression(expressionStr);
            Boolean result = exp.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Expression evaluation failed: [{}]", expressionStr, e);
            return false;
        }
    }

    private Map<String, Object> resolveParams(Map<String, String> paramExprs, StandardEvaluationContext context) {
        Map<String, Object> params = new HashMap<>();
        if (paramExprs == null) return params;
        
        for (Map.Entry<String, String> entry : paramExprs.entrySet()) {
            try {
                Expression exp = parser.parseExpression(entry.getValue());
                Object value = exp.getValue(context);
                params.put(entry.getKey(), value);
            } catch (Exception e) {
                log.warn("Param evaluation failed: [{}]", entry.getValue(), e);
            }
        }
        return params;
    }

    @Override
    public void executeAction(Node node, String actionName, Map<String, Object> params) {
        // Validate against Schema
        TaskSchema schema = schemaRegistry.get(node.getTaskConfig().getTaskType());
        if (schema == null) {
            log.error("Unknown task type: {}", node.getTaskConfig().getTaskType());
            return;
        }

        ActionDefinition actionDef = schema.getActions().get(actionName);
        if (actionDef == null) {
            log.error("Action [{}] not supported by task type [{}]", actionName, schema.getType());
            return;
        }

        log.info("Triggering Action [{}] on Node [{}]", actionName, node.getId());
        taskExecutor.executeAction(node, actionDef, params);
    }
}
