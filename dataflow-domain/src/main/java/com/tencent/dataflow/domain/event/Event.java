package com.tencent.dataflow.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event - 领域事件
 * <p>
 * 系统中传递的消息单元。遵循 CloudEvents 规范的核心语义。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    /**
     * 事件唯一标识 (UUID)
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 事件类型
     * <p>
     * 格式建议: {domain}.{entity}.{action}
     * 示例: "task.node.succeeded", "system.maintenance.started"
     * </p>
     */
    private String type;

    /**
     * 事件源
     * <p>
     * 标识事件的产生者。
     * 示例: "/pipelines/{pipelineId}/nodes/{nodeId}"
     * </p>
     */
    private String source;

    /**
     * 事件发生时间
     */
    @Builder.Default
    private Instant time = Instant.now();

    /**
     * 关联的 Pipeline ID (上下文)
     */
    private String pipelineId;

    /**
     * 关联的 Execution ID (上下文)
     * <p>
     * 标识产生此事件的具体执行实例。
     * - 对于 Batch 任务：代表具体的批次 ID (Run ID)。
     * - 对于 Streaming 任务：代表具体的部署实例 ID。
     * 注意：上下游节点不一定共享同一个 executionId (混合编排场景)。
     * </p>
     */
    private String executionId;

    /**
     * 逻辑关联 ID (上下文)
     * <p>
     * 用于跨 Execution 串联业务逻辑。
     * 例如：Batch A 产生数据版本 V1，Streaming B 处理 V1，它们通过 V1 关联。
     * </p>
     */
    private String correlationId;

    /**
     * 事件负载 (Payload)
     * <p>
     * 包含具体的业务数据。
     * 节点产生的事件通常包含: output, metrics 等。
     * </p>
     */
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    /**
     * 扩展属性 (Headers)
     * <p>
     * 用于传递链路追踪信息、优先级等元数据。
     * </p>
     */
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();
}
