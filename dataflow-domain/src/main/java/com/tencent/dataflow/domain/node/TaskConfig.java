package com.tencent.dataflow.domain.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * TaskConfig - 任务配置
 * <p>
 * 定义节点运行的具体任务类型和参数。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfig {

    /**
     * 任务类型 (引用 TaskSchema.type)
     * 必需
     */
    private String taskType;

    /**
     * 任务具体配置
     * <p>
     * 结构由 TaskSchema.executionConfigSchema 约束。
     * 例如: {"sql": "SELECT * FROM table", "parallelism": 4}
     * </p>
     */
    private Map<String, Object> config;

    /**
     * 任务定义引用 (可选)
     * <p>
     * 如果指定，则复用 TaskDefinition 中的配置作为基准，
     * 当前 config 中的属性将覆盖 TaskDefinition 中的配置。
     * 格式: "namespace:name:version"
     * </p>
     */
    private String taskDefinitionRef;
}
