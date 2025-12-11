package com.tencent.dataflow.domain.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Node - 节点 (任务实例)
 * <p>
 * 流水线中的执行单元，包含任务配置和控制策略。
 * Node 是 TaskSchema 的实例化体现。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {

    /**
     * 节点 ID (Pipeline 内唯一)
     */
    private String id;

    /**
     * 节点名称 (可读性更好)
     */
    private String name;

    /**
     * 节点描述
     */
    private String description;

    /**
     * 任务配置 (定义"做什么")
     */
    private TaskConfig taskConfig;

    /**
     * 控制策略 (定义"怎么控制")
     * <p>
     * 包含 stopWhen, restartWhen, retryWhen 等反应式控制逻辑
     * </p>
     */
    private ControlPolicy controlPolicy;

    /**
     * 触发条件表达式
     * <p>
     * 定义节点何时启动。
     * 示例: "event:upstream_node.succeeded"
     * </p>
     */
    private String startWhen;

    /**
     * 启动参数映射
     * <p>
     * 定义如何从触发事件或其他上下文中提取数据作为任务输入。
     * Key: 任务输入参数名
     * Value: 表达式 (如 "{{ event.payload.outputPath }}")
     * </p>
     */
    private Map<String, String> startPayload;

    /**
     * 自定义元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 校验节点配置有效性
     */
    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be empty");
        }
        if (taskConfig == null) {
            throw new IllegalArgumentException("TaskConfig cannot be null");
        }
        if (taskConfig.getTaskType() == null && taskConfig.getTaskDefinitionRef() == null) {
            throw new IllegalArgumentException("Either taskType or taskDefinitionRef must be specified");
        }
    }
}
