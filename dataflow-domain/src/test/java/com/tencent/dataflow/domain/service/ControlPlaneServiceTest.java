package com.tencent.dataflow.domain.service;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.MockTaskExecutor;
import com.tencent.dataflow.domain.node.ControlPolicy;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.PolicyRule;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import com.tencent.dataflow.domain.taskschema.example.ExampleTaskSchemas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ControlPlaneServiceTest {

    private ControlPlaneServiceImpl controlPlaneService;
    private MockTaskExecutor mockExecutor;
    private Map<String, Node> nodeStore;
    private Map<String, TaskSchema> schemaRegistry;

    @BeforeEach
    void setUp() {
        mockExecutor = new MockTaskExecutor();
        nodeStore = new HashMap<>();
        schemaRegistry = new HashMap<>();

        // Register Schemas
        TaskSchema shellSchema = ExampleTaskSchemas.shellTaskSchema();
        TaskSchema flinkSchema = ExampleTaskSchemas.flinkStreamingTaskSchema();
        schemaRegistry.put(shellSchema.getType(), shellSchema);
        schemaRegistry.put(flinkSchema.getType(), flinkSchema);

        // Mock Repository
        NodeRepository mockRepo = new NodeRepository() {
            @Override
            public Node findById(String nodeId) {
                return nodeStore.get(nodeId);
            }

            @Override
            public List<Node> findAllActiveNodes() {
                return new ArrayList<>(nodeStore.values());
            }
        };

        controlPlaneService = new ControlPlaneServiceImpl(mockRepo, mockExecutor, schemaRegistry);
    }

    @Test
    void testStandardPolicy_StopWhen() {
        // 1. Create a Flink Node
        Node node = Node.builder()
                .id("flink-job-1")
                .taskConfig(TaskConfig.builder().taskType("flink_streaming").build())
                .controlPolicy(ControlPolicy.builder()
                        // Expression: Stop when event type is MAINTENANCE
                        .stopWhen("#event.type == 'MAINTENANCE'")
                        .build())
                .build();
        nodeStore.put(node.getId(), node);

        // 2. Send Event
        Event event = Event.builder()
                .type("MAINTENANCE")
                .build();
        
        controlPlaneService.onEvent(event);

        // 3. Assert
        List<String> history = mockExecutor.getActionHistory(node.getId());
        assertTrue(history.contains(ActionDefinition.ACTION_STOP), "Node should have been stopped");
        
        // Verify side effect
        assertEquals("STOPPED", mockExecutor.getState(node, com.tencent.dataflow.domain.taskschema.StateDefinition.builder().name("status").build()).toString()); // MockExecutor ignores StateDefinition for simple get
    }

    @Test
    void testCustomRule_Scale() {
        // 1. Create a Flink Node with Custom Rule
        PolicyRule scaleRule = PolicyRule.builder()
                .name("Auto Scale")
                .condition("#event.type == 'metrics_update' && #event.payload['lag'] > 1000")
                .action("scale")
                .actionParams(Map.of("replicas", "5"))
                .build();

        Node node = Node.builder()
                .id("flink-job-2")
                .taskConfig(TaskConfig.builder().taskType("flink_streaming").build())
                .controlPolicy(ControlPolicy.builder()
                        .customRules(List.of(scaleRule))
                        .build())
                .build();
        nodeStore.put(node.getId(), node);

        // 2. Send Event
        Map<String, Object> payload = new HashMap<>();
        payload.put("lag", 2000);
        
        Event event = Event.builder()
                .type("metrics_update")
                .payload(payload)
                .build();

        controlPlaneService.onEvent(event);

        // 3. Assert
        List<String> history = mockExecutor.getActionHistory(node.getId());
        assertTrue(history.contains("scale"), "Node should have triggered scale action");
        
        // Verify side effect (MockExecutor sets 'parallelism' state for 'scale' action)
        // We need to manually check the internal state of MockExecutor or use getState
        // MockExecutor.getState implementation:
        // return (states != null) ? states.get(stateName) : null;
        // We need a dummy StateDefinition to query "parallelism"
        // But MockExecutor doesn't check StateDefinition details, just name.
        Object parallelism = mockExecutor.getState(node, com.tencent.dataflow.domain.taskschema.StateDefinition.builder().name("parallelism").build());
        assertEquals(5, parallelism);
    }
    
    @Test
    void testPolicy_NoMatch() {
        // 1. Create Node
        Node node = Node.builder()
                .id("job-3")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                .controlPolicy(ControlPolicy.builder()
                        .stopWhen("#event.type == 'CRITICAL_ERROR'")
                        .build())
                .build();
        nodeStore.put(node.getId(), node);

        // 2. Send Irrelevant Event
        Event event = Event.builder()
                .type("NORMAL_INFO")
                .build();

        controlPlaneService.onEvent(event);

        // 3. Assert
        List<String> history = mockExecutor.getActionHistory(node.getId());
        assertTrue(history.isEmpty(), "No action should be triggered");
    }
}
