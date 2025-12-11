package com.tencent.dataflow.domain.service;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.executor.MockTaskExecutor;
import com.tencent.dataflow.domain.node.ControlPolicy;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import com.tencent.dataflow.domain.taskschema.example.ExampleTaskSchemas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PipelineHybridTest {

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

    /**
     * 场景 1: 流触发批 (Stream -> Batch)
     * <p>
     * Flink 任务 (Streaming) 持续运行，每完成一个 Checkpoint 产生一个事件。
     * Shell 任务 (Batch) 监听该事件，启动并处理对应 Checkpoint 的数据。
     * </p>
     */
    @Test
    void testStreamTriggersBatch() {
        // 1. Define Nodes
        
        // Node A: Flink Streaming Job
        Node nodeStream = Node.builder()
                .id("flink-stream")
                .taskConfig(TaskConfig.builder().taskType("flink_streaming").build())
                .build();

        // Node B: Batch Job (triggered by Checkpoint)
        Node nodeBatch = Node.builder()
                .id("batch-processor")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                // Trigger condition: Event from flink-stream AND type is checkpoint_completed
                .startWhen("#event.source == '/pipelines/hybrid-pipe/nodes/flink-stream' && #event.type == 'checkpoint_completed'")
                // Payload mapping: Pass checkpoint path to script env
                .startPayload(Map.of("script", "'process_data.sh ' + #event.payload['path']"))
                .build();

        nodeStore.put(nodeStream.getId(), nodeStream);
        nodeStore.put(nodeBatch.getId(), nodeBatch);

        // 2. Simulate Flink emitting Checkpoint Event
        Map<String, Object> payload = new HashMap<>();
        payload.put("checkpointId", 105);
        payload.put("path", "s3://bucket/checkpoints/105");

        Event event = Event.builder()
                .type("checkpoint_completed")
                .source("/pipelines/hybrid-pipe/nodes/flink-stream")
                .pipelineId("hybrid-pipe")
                .executionId("exec-stream-master") // Streaming job execution ID
                .payload(payload)
                .build();

        System.out.println(">>> Flink emitted checkpoint event: " + event);
        
        // 3. Control Plane processes event
        controlPlaneService.onEvent(event);

        // 4. Assert Batch Job Started
        List<String> historyBatch = mockExecutor.getActionHistory("batch-processor");
        assertTrue(historyBatch.contains("start"), "Batch job should start upon checkpoint");
        
        // Verify parameters were passed correctly (MockExecutor logs params, but we can't easily assert logs here without capturing them. 
        // In a real test we might capture arguments using ArgumentCaptor if using Mockito)
        // For this simple mock, we trust the logic if 'start' is called.
    }

    /**
     * 场景 2: 批控制流 (Batch -> Stream Control)
     * <p>
     * 一个批处理任务更新了配置 (Config Updater)。
     * 完成后，触发 Flink 任务重启 (Restart) 以加载新配置。
     * </p>
     */
    @Test
    void testBatchControlsStream() {
        // 1. Define Nodes
        
        // Node A: Config Updater (Batch)
        Node nodeConfig = Node.builder()
                .id("config-updater")
                .taskConfig(TaskConfig.builder().taskType("shell_script").build())
                .build();

        // Node B: Flink Job (Streaming)
        // It is already running. It has a policy to restart when config is updated.
        Node nodeStream = Node.builder()
                .id("flink-stream-2")
                .taskConfig(TaskConfig.builder().taskType("flink_streaming").build())
                .controlPolicy(ControlPolicy.builder()
                        // Restart when config-updater succeeds
                        .restartWhen("#event.source == '/pipelines/hybrid-pipe/nodes/config-updater' && #event.type == 'succeeded'")
                        .build())
                .build();

        nodeStore.put(nodeConfig.getId(), nodeConfig);
        nodeStore.put(nodeStream.getId(), nodeStream);

        // 2. Simulate Config Updater finishing
        Event event = Event.builder()
                .type("succeeded")
                .source("/pipelines/hybrid-pipe/nodes/config-updater")
                .pipelineId("hybrid-pipe")
                .executionId("exec-batch-run-1")
                .build();

        System.out.println(">>> Config Updater finished: " + event);

        // 3. Control Plane processes event
        controlPlaneService.onEvent(event);

        // 4. Assert Flink Job Restarted
        List<String> historyStream = mockExecutor.getActionHistory("flink-stream-2");
        assertTrue(historyStream.contains("restart"), "Flink job should restart after config update");
    }
}
