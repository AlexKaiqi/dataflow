package com.tencent.dataflow.app.service;

import com.tencent.dataflow.app.parser.PipelineYamlParser;
import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.TaskExecutor;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.pipeline.Pipeline;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.StateDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PipelineYamlFlowTest {

    private PipelineAppService pipelineAppService;
    private MockTaskExecutor mockExecutor;
    private Map<String, Node> nodeStore;

    @BeforeEach
    void setUp() {
        nodeStore = new HashMap<>();
        mockExecutor = new MockTaskExecutor();
        
        NodeRepository nodeRepo = new NodeRepository() {
            @Override
            public Node findById(String nodeId) {
                return nodeStore.get(nodeId);
            }
            @Override
            public List<Node> findAllActiveNodes() {
                return new ArrayList<>(nodeStore.values());
            }
            @Override
            public void save(Node node) {
                nodeStore.put(node.getId(), node);
            }
        };

        Map<String, TaskSchema> schemaRegistry = new HashMap<>();
        // Register a dummy schema for "shell_script"
        TaskSchema shellSchema = new TaskSchema();
        shellSchema.setType("shell_script");
        shellSchema.setActions(Map.of("start", ActionDefinition.builder().name("start").build()));
        schemaRegistry.put("shell_script", shellSchema);

        ControlPlaneServiceImpl controlPlaneService = new ControlPlaneServiceImpl(nodeRepo, mockExecutor, schemaRegistry);
        PipelineYamlParser parser = new PipelineYamlParser();
        
        pipelineAppService = new PipelineAppService(parser, nodeRepo, controlPlaneService);
    }

    @Test
    void testYamlPipelineFlow() {
        String yaml = """
            id: yaml-pipeline
            description: A pipeline from YAML
            nodes:
              - id: node-a
                type: shell_script
                config:
                  script: echo A
              - id: node-b
                type: shell_script
                config:
                  script: echo B
                startWhen: "#event.source == '/pipelines/yaml-pipeline/nodes/node-a' && #event.type == 'succeeded'"
            """;

        // 1. Submit Pipeline
        Pipeline pipeline = pipelineAppService.submitPipeline(yaml);
        assertNotNull(pipeline);
        assertEquals("yaml-pipeline", pipeline.getId());
        assertEquals(2, nodeStore.size());

        // 2. Start Node A
        System.out.println(">>> Starting Node A...");
        pipelineAppService.executeAction("node-a", "start", Map.of());
        assertTrue(mockExecutor.hasExecuted("node-a", "start"));

        // 3. Simulate Node A Success
        System.out.println(">>> Node A Succeeded...");
        Event event = Event.builder()
                .type("succeeded")
                .source("/pipelines/yaml-pipeline/nodes/node-a")
                .pipelineId("yaml-pipeline")
                .build();
        pipelineAppService.triggerEvent(event);

        // 4. Verify Node B Started
        System.out.println(">>> Verifying Node B...");
        assertTrue(mockExecutor.hasExecuted("node-b", "start"));
    }

    // Simple Mock Executor
    static class MockTaskExecutor implements TaskExecutor {
        private final Map<String, List<String>> history = new ConcurrentHashMap<>();

        @Override
        public String executeAction(Node node, ActionDefinition action, Map<String, Object> params) {
            System.out.println("EXEC: Node[" + node.getId() + "] Action[" + action.getName() + "]");
            history.computeIfAbsent(node.getId(), k -> new ArrayList<>()).add(action.getName());
            return "OK";
        }

        @Override
        public Object getState(Node node, StateDefinition state) {
            return null;
        }

        public boolean hasExecuted(String nodeId, String action) {
            return history.getOrDefault(nodeId, Collections.emptyList()).contains(action);
        }
    }
}
