package com.tencent.dataflow.domain.executor;

import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.StateDefinition;
// import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MockTaskExecutor - 测试用执行器
 * <p>
 * 模拟执行 Action 和查询 State，不进行真实的网络调用。
 * 内部维护一个简单的状态存储，用于验证控制流逻辑。
 * </p>
 */
// @Slf4j
public class MockTaskExecutor implements TaskExecutor {

    // 模拟存储每个节点的状态: NodeId -> {StateName -> Value}
    private final Map<String, Map<String, Object>> nodeStates = new ConcurrentHashMap<>();

    // 记录 Action 调用历史: NodeId -> List<ActionName>
    private final Map<String, java.util.List<String>> actionHistory = new ConcurrentHashMap<>();

    @Override
    public Object executeAction(Node node, ActionDefinition action, Map<String, Object> params) {
        String nodeId = node.getId();
        String actionName = action.getName();
        
        System.out.println("Executing action [" + actionName + "] on node [" + nodeId + "] with params: " + params);
        
        // 记录调用
        actionHistory.computeIfAbsent(nodeId, k -> new java.util.ArrayList<>()).add(actionName);

        // 模拟行为产生的副作用 (Side Effects)
        Map<String, Object> states = nodeStates.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>());
        
        switch (actionName) {
            case ActionDefinition.ACTION_START:
            case ActionDefinition.ACTION_RESTART:
            case ActionDefinition.ACTION_RESUME:
                states.put(StateDefinition.STATE_STATUS, "RUNNING");
                break;
            case ActionDefinition.ACTION_STOP:
            case ActionDefinition.ACTION_PAUSE:
                states.put(StateDefinition.STATE_STATUS, "STOPPED");
                break;
            case "scale":
                // 模拟扩展行为修改状态
                states.put("parallelism", params.get("replicas"));
                break;
            case "approve":
                states.put(StateDefinition.STATE_STATUS, "APPROVED");
                break;
            case "reject":
                states.put(StateDefinition.STATE_STATUS, "REJECTED");
                break;
            default:
                System.out.println("Unknown action side effect for: " + actionName);
        }

        return "OK";
    }

    @Override
    public Object getState(Node node, StateDefinition state) {
        String nodeId = node.getId();
        String stateName = state.getName();
        
        Map<String, Object> states = nodeStates.get(nodeId);
        Object value = (states != null) ? states.get(stateName) : null;
        
        System.out.println("Querying state [" + stateName + "] on node [" + nodeId + "], result: " + value);
        return value;
    }

    // --- 测试辅助方法 ---

    public void setNodeState(String nodeId, String stateName, Object value) {
        nodeStates.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>()).put(stateName, value);
    }

    public java.util.List<String> getActionHistory(String nodeId) {
        return actionHistory.getOrDefault(nodeId, java.util.Collections.emptyList());
    }
    
    public void clear() {
        nodeStates.clear();
        actionHistory.clear();
    }
}
