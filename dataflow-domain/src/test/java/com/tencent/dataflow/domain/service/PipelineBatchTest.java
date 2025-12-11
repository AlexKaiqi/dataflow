package com.tencent.dataflow.domain.service;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.MockTaskExecutor;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.EventDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import com.tencent.dataflow.domain.taskschema.example.ExampleTaskSchemas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PipelineBatchTest {

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
        schemaRegistry.put(shellSchema.getType(), shellSchema);

        // Mock Repository
        NodeRepository mockRepo = new com.tencent.dataflow.domain.repository.InMemoryNodeRepository(nodeStore);

        controlPlaneService = new ControlPlaneServiceImpl(mockRepo, mockExecutor, schemaRegistry);
    }

    @Test
    void testBatchPipeline_A_to_B() {
        // 1. Define Pipeline: Node A -> Node B
        
        // Node A (Head Node)
        Node nodeA = Node.builder()
                .id("node-a")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                .build();
        
        // Node B (Dependent Node)
        // startWhen: A succeeded
        Node nodeB = Node.builder()
                .id("node-b")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                .startWhen("#event.source == '/pipelines/pipe-1/nodes/node-a' && #event.type == 'succeeded'")
                .startPayload(Map.of("script", "'echo Hello from B, triggered by ' + #event.source"))
                .build();

        // Store nodes
        nodeStore.put(nodeA.getId(), nodeA);
        nodeStore.put(nodeB.getId(), nodeB);

        // 2. Simulate Execution Flow

        // Step 1: Manually Start A (External Trigger)
        // In real system, this might be a "pipeline.start" event or API call
        System.out.println(">>> Starting Node A manually...");
        mockExecutor.executeAction(nodeA, ActionDefinition.builder().name("start").build(), Map.of("script", "echo A"));
        
        // Verify A started
        assertTrue(mockExecutor.getActionHistory("node-a").contains("start"));

        // Step 2: Node A finishes successfully -> Emits 'succeeded' event
        System.out.println(">>> Node A finished, emitting event...");
        Event eventA = Event.builder()
                .type(EventDefinition.EVENT_SUCCEEDED)
                .source("/pipelines/pipe-1/nodes/node-a")
                .pipelineId("pipe-1")
                .executionId("exec-1")
                .build();
        
        // Step 3: Control Plane processes the event
        controlPlaneService.onEvent(eventA);

        // 4. Assert Node B started
        List<String> historyB = mockExecutor.getActionHistory("node-b");
        System.out.println(">>> Node B History: " + historyB);
        assertTrue(historyB.contains("start"), "Node B should have started after A succeeded");
    }
}
