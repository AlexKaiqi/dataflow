package com.tencent.dataflow.domain.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PolicyRule - 通用策略规则
 * <p>
 * 允许定义任意的 "Event -> Action" 映射，以支持 TaskSchema 中定义的扩展行为。
 * 弥补了 ControlPolicy 中标准字段（stopWhen 等）无法覆盖复杂场景的不足。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRule {
    
    /**
     * 规则名称/描述
     */
    private String name;

    /**
     * 触发条件表达式
     * <p>
     * 当表达式求值为 true 时，触发指定行为。
     * 示例: "event.type == 'TRAFFIC_SPIKE' && event.payload.qps > 1000"
     * </p>
     */
    private String condition;

    /**
     * 触发的行为名称
     * <p>
     * 必须是 TaskSchema.actions 中定义的行为。
     * 示例: "scale_up", "switch_traffic", "create_savepoint"
     * </p>
     */
    private String action;

    /**
     * 行为参数映射
     * <p>
     * 定义如何构造 Action 的调用参数。
     * Key: Action 参数名
     * Value: 表达式 (支持从 event, node, context 中提取数据)
     * 示例: {"replicas": "event.payload.suggested_replicas + 1"}
     * </p>
     */
    private Map<String, String> actionParams;
}
