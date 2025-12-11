package com.tencent.dataflow.domain.service;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.MockTaskExecutor;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.pipeline.Pipeline;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.EventDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import com.tencent.dataflow.domain.taskschema.example.ExampleTaskSchemas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PipelineApprovalTest {

    private ControlPlaneServiceImpl controlPlaneService;
    private MockTaskExecutor mockExecutor;
    private Map<String, Node> nodeStore;
    private Map<String, TaskSchema> schemaRegistry;

    @BeforeEach
    void setUp() {
        mockExecutor = new MockTaskExecutor();
        nodeStore = new HashMap<>();
        schemaRegistry = new HashMap<>();

        // 1. Register Shell Schema
        TaskSchema shellSchema = ExampleTaskSchemas.shellTaskSchema();
        schemaRegistry.put(shellSchema.getType(), shellSchema);

        // 2. Register Approval Schema
        TaskSchema approvalSchema = createApprovalTaskSchema();
        schemaRegistry.put(approvalSchema.getType(), approvalSchema);

        // 3. Mock Repository
        NodeRepository mockRepo = new com.tencent.dataflow.domain.repository.InMemoryNodeRepository(nodeStore);

        controlPlaneService = new ControlPlaneServiceImpl(mockRepo, mockExecutor, schemaRegistry);
    }

    private TaskSchema createApprovalTaskSchema() {
        TaskSchema schema = new TaskSchema();
        schema.setType("approval");
        schema.setDescription("Human Approval Task");
        schema.setCreatedAt(Instant.now());
        schema.setCreatedBy("system");

        // Actions
        Map<String, ActionDefinition> actions = new HashMap<>();
        actions.put("start", ActionDefinition.builder().name("start").build()); // Start the approval process
        actions.put("approve", ActionDefinition.builder().name("approve").build());
        actions.put("reject", ActionDefinition.builder().name("reject").build());
        schema.setActions(actions);

        // Events
        List<EventDefinition> events = new ArrayList<>();
        events.add(EventDefinition.builder().name("started").build());
        events.add(EventDefinition.builder().name("approval_requested").build());
        events.add(EventDefinition.builder().name("approved").build());
        events.add(EventDefinition.builder().name("rejected").build());
        events.add(EventDefinition.builder().name("succeeded").build()); // Standard success
        schema.setEvents(events);

        return schema;
    }

    @Test
    void testApprovalPipeline() {
        // Scenario: PreCheck (Shell) -> Approval (Human) -> Deploy (Shell)

        // 1. Define Nodes
        Node nodePreCheck = Node.builder()
                .id("node-precheck")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                .build();

        Node nodeApproval = Node.builder()
                .id("node-approval")
                .taskConfig(TaskConfig.builder().taskType("approval").build())
                .startWhen("#event.source == '/pipelines/pipe-approval/nodes/node-precheck' && #event.type == 'succeeded'")
                .build();

        Node nodeDeploy = Node.builder()
                .id("node-deploy")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                // Start when Approval is APPROVED (or SUCCEEDED)
                .startWhen("#event.source == '/pipelines/pipe-approval/nodes/node-approval' && #event.type == 'approved'")
                .build();

        Pipeline.builder()
                .id("pipe-approval")
                .nodes(List.of(nodePreCheck, nodeApproval, nodeDeploy))
                .build();

        nodeStore.put(nodePreCheck.getId(), nodePreCheck);
        nodeStore.put(nodeApproval.getId(), nodeApproval);
        nodeStore.put(nodeDeploy.getId(), nodeDeploy);

        // 2. Start PreCheck
        System.out.println(">>> [Step 1] Starting PreCheck...");
        mockExecutor.executeAction(nodePreCheck, ActionDefinition.builder().name("start").build(), Map.of());
        
        // Simulate PreCheck Success
        Event eventPreCheckSuccess = Event.builder()
                .type("succeeded")
                .source("/pipelines/pipe-approval/nodes/node-precheck")
                .pipelineId("pipe-approval")
                .build();
        controlPlaneService.onEvent(eventPreCheckSuccess);

        // 3. Verify Approval Node Started
        System.out.println(">>> [Step 2] Verifying Approval Node Started...");
        assertTrue(mockExecutor.getActionHistory("node-approval").contains("start"), "Approval node should have started");

        // Simulate Approval Node emitting 'approval_requested'
        Event eventApprovalRequested = Event.builder()
                .type("approval_requested")
                .source("/pipelines/pipe-approval/nodes/node-approval")
                .pipelineId("pipe-approval")
                .build();
        controlPlaneService.onEvent(eventApprovalRequested);
        
        // 4. User Approves
        System.out.println(">>> [Step 3] User Approves...");
        // User calls 'approve' action
        controlPlaneService.executeAction(nodeApproval, "approve", Map.of("approver", "admin"));
        
        // Verify 'approve' action was sent to executor
        assertTrue(mockExecutor.getActionHistory("node-approval").contains("approve"), "Approve action should be executed");

        // 5. Simulate Approval System Response (Approved)
        System.out.println(">>> [Step 4] Approval System Confirms...");
        Event eventApproved = Event.builder()
                .type("approved")
                .source("/pipelines/pipe-approval/nodes/node-approval")
                .pipelineId("pipe-approval")
                .build();
        controlPlaneService.onEvent(eventApproved);

        // 6. Verify Deploy Node Started
        System.out.println(">>> [Step 5] Verifying Deploy Node Started...");
        assertTrue(mockExecutor.getActionHistory("node-deploy").contains("start"), "Deploy node should have started after approval");
    }
}
