package com.tencent.dataflow.domain.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ControlPolicy - 节点控制策略
 * <p>
 * 定义节点如何响应外部事件进行自我控制。
 * 采用反应式控制模式：Event -> Expression -> Action
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlPolicy {

    /**
     * 停止条件表达式 (Streaming 任务)
     * <p>
     * 当表达式求值为 true 时，触发 TaskSchema 定义的 'stop' 行为。
     * 上下文包含: event, node, context
     * 示例: "event.type == 'MAINTENANCE_WINDOW_START'"
     * </p>
     */
    private String stopWhen;

    /**
     * 重启条件表达式 (Streaming 任务)
     * <p>
     * 当表达式求值为 true 时，触发 TaskSchema 定义的 'restart' 行为。
     * 示例: "event.type == 'CONFIG_UPDATED' && event.payload.targetNodeId == node.id"
     * </p>
     */
    private String restartWhen;

    /**
     * 重试条件表达式 (Batch 任务)
     * <p>
     * 当任务失败且表达式求值为 true 时，触发 'retry' 行为。
     * 上下文包含: error, retryCount
     * 示例: "error.code == 'NETWORK_TIMEOUT' && retryCount < 3"
     * </p>
     */
    private String retryWhen;

    /**
     * 告警条件表达式 (通用)
     * <p>
     * 当表达式求值为 true 时，触发系统告警，但不影响任务运行。
     * 示例: "metrics.lag > 10000"
     * </p>
     */
    private String alertWhen;
    
    /**
     * 跳过条件表达式 (Batch 任务)
     * <p>
     * 当表达式求值为 true 时，跳过当前任务执行，标记为 SKIPPED。
     * 示例: "context.isHoliday == true"
     * </p>
     */
    private String skipWhen;

    /**
     * 自定义策略规则列表
     * <p>
     * 用于支持 TaskSchema 中定义的扩展行为（非标准生命周期行为）。
     * 允许灵活定义 "Event -> Action" 的映射关系。
     * </p>
     */
    private java.util.List<PolicyRule> customRules;
}
