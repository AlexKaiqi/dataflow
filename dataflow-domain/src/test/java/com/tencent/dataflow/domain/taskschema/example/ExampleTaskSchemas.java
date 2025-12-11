package com.tencent.dataflow.domain.taskschema.example;

import com.tencent.dataflow.domain.taskschema.*;

import java.time.Instant;
import java.util.*;

/**
 * ExampleTaskSchemas - 示例 TaskSchema 定义
 * <p>
 * 提供几个典型的 TaskSchema 示例，用于测试和演示。
 * </p>
 */
public class ExampleTaskSchemas {

    /**
     * 示例 1: Shell 脚本任务 (Batch)
     * <p>
     * 最简单的批处理任务。
     * 支持行为: start, stop
     * 产生事件: started, succeeded, failed
     * </p>
     */
    public static TaskSchema shellTaskSchema() {
        TaskSchema schema = new TaskSchema();
        schema.setType("shell_script");
        schema.setDescription("Execute a shell script");
        schema.setCreatedAt(Instant.now());
        schema.setCreatedBy("system");

        // 1. 定义 Actions
        Map<String, ActionDefinition> actions = new HashMap<>();
        
        // Start Action
        actions.put(ActionDefinition.ACTION_START, ActionDefinition.builder()
                .name(ActionDefinition.ACTION_START)
                .description("Start the shell script")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/start") // POST /start
                .build());

        // Stop Action
        actions.put(ActionDefinition.ACTION_STOP, ActionDefinition.builder()
                .name(ActionDefinition.ACTION_STOP)
                .description("Kill the process")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/stop") // POST /stop
                .build());
        
        schema.setActions(actions);

        // 2. 定义 Events
        List<EventDefinition> events = new ArrayList<>();
        events.add(EventDefinition.builder().name(EventDefinition.EVENT_STARTED).description("Script started").build());
        events.add(EventDefinition.builder().name(EventDefinition.EVENT_SUCCEEDED).description("Script finished successfully").build());
        events.add(EventDefinition.builder().name(EventDefinition.EVENT_FAILED).description("Script failed").build());
        schema.setEvents(events);

        // 3. 定义 States
        Map<String, StateDefinition> states = new HashMap<>();
        states.put(StateDefinition.STATE_STATUS, StateDefinition.builder()
                .name(StateDefinition.STATE_STATUS)
                .type("string")
                .possibleValues(Arrays.asList("PENDING", "RUNNING", "SUCCEEDED", "FAILED"))
                .endpoint("/status") // GET /status
                .build());
        schema.setStates(states);

        // 4. 定义 Execution Config Schema
        Map<String, Object> configSchema = new HashMap<>();
        configSchema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("script", Map.of("type", "string", "description", "The shell script content"));
        properties.put("env", Map.of("type", "object", "description", "Environment variables"));
        configSchema.put("properties", properties);
        configSchema.put("required", List.of("script"));
        schema.setExecutionConfigSchema(configSchema);

        return schema;
    }

    /**
     * 示例 2: Flink Streaming 任务 (Streaming)
     * <p>
     * 复杂的流式任务。
     * 支持行为: start, stop, savepoint, scale_up (扩展)
     * 产生事件: started, failed, checkpoint_completed, metrics_update
     * </p>
     */
    public static TaskSchema flinkStreamingTaskSchema() {
        TaskSchema schema = new TaskSchema();
        schema.setType("flink_streaming");
        schema.setDescription("Apache Flink Streaming Job");
        schema.setCreatedAt(Instant.now());
        schema.setCreatedBy("system");

        // 1. Actions
        Map<String, ActionDefinition> actions = new HashMap<>();
        
        // Standard: Start
        actions.put(ActionDefinition.ACTION_START, ActionDefinition.builder()
                .name(ActionDefinition.ACTION_START)
                .protocol(AccessProtocol.K8S) // 假设通过 K8S Operator 操作
                .endpoint("flink.apache.org/v1beta1/FlinkDeployment")
                .build());

        // Standard: Stop (with savepoint)
        actions.put(ActionDefinition.ACTION_STOP, ActionDefinition.builder()
                .name(ActionDefinition.ACTION_STOP)
                .description("Stop with savepoint")
                .protocol(AccessProtocol.K8S)
                .build());

        // Standard: Restart
        actions.put(ActionDefinition.ACTION_RESTART, ActionDefinition.builder()
                .name(ActionDefinition.ACTION_RESTART)
                .description("Restart job")
                .protocol(AccessProtocol.K8S)
                .build());

        // Custom: Trigger Savepoint
        actions.put("trigger_savepoint", ActionDefinition.builder()
                .name("trigger_savepoint")
                .description("Manually trigger a savepoint")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/jobs/{executionId}/savepoints")
                .build());
        
        // Custom: Scale
        actions.put("scale", ActionDefinition.builder()
                .name("scale")
                .description("Scale parallelism")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/jobs/{executionId}/scale")
                .build());

        schema.setActions(actions);

        // 2. Events
        List<EventDefinition> events = new ArrayList<>();
        events.add(EventDefinition.builder().name(EventDefinition.EVENT_STARTED).build());
        events.add(EventDefinition.builder().name(EventDefinition.EVENT_FAILED).build());
        events.add(EventDefinition.builder().name("checkpoint_completed").description("Checkpoint finished").build());
        events.add(EventDefinition.builder().name("metrics_update").description("Periodic metrics report").build());
        schema.setEvents(events);

        // 3. States
        Map<String, StateDefinition> states = new HashMap<>();
        states.put(StateDefinition.STATE_STATUS, StateDefinition.builder()
                .name(StateDefinition.STATE_STATUS)
                .type("string")
                .possibleValues(Arrays.asList("CREATED", "RUNNING", "FAILING", "RESTARTING", "FINISHED"))
                .build());
        
        // Complex State: Metrics
        states.put(StateDefinition.STATE_METRICS, StateDefinition.builder()
                .name(StateDefinition.STATE_METRICS)
                .type("object")
                .description("Current job metrics (throughput, lag)")
                .build());
        
        schema.setStates(states);

        return schema;
    }
}
