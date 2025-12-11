package com.tencent.dataflow.domain.taskschema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * StateDefinition - 状态定义（值对象）
 * <p>
 * 定义任务可能处于的状态，以及如何查询该状态。
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateDefinition {

    // 标准状态名称常量
    public static final String STATE_STATUS = "status";           // 运行状态 (RUNNING, SUCCEEDED...)
    public static final String STATE_PROGRESS = "progress";       // 进度 (0-100)
    public static final String STATE_METRICS = "metrics";         // 性能指标 (cpu, memory...)
    public static final String STATE_CHECKPOINT = "checkpoint";   // 最近的检查点信息

    /**
     * 状态名称（如 status, trainingProgress, accuracy）
     */
    private String name;
    
    /**
     * 状态描述
     */
    private String description;
    
    /**
     * 状态类型（string, number, boolean, object）
     */
    private String type;

    /**
     * 访问协议
     * <p>
     * 定义如何获取该状态。默认为 HTTP。
     * </p>
     */
    @Builder.Default
    private AccessProtocol protocol = AccessProtocol.HTTP;

    /**
     * 获取状态的端点路径/选择器
     * <p>
     * 根据协议不同，含义不同：
     * - HTTP: URL 路径 (支持 {executionId} 占位符)。若为空，默认为 "/{name}" (GET)。
     * - GRPC: Method Name。
     * - INTERNAL: BeanName/MethodName。
     * </p>
     */
    private String endpoint;

    /**
     * 协议扩展配置
     */
    private java.util.Map<String, Object> protocolConfig;

    /**
     * 状态值的结构定义 (JSON Schema)
     * <p>
     * 如果 type 是 object，这里定义对象的内部结构。
     * 方便前端展示或后端表达式解析校验。
     * </p>
     */
    private java.util.Map<String, Object> valueSchema;

    /**
     * 可能的枚举值列表 (仅当 type 为 string/enum 时有效)
     * <p>
     * 定义该状态可能返回的所有值，方便 UI 下拉选择或校验。
     * 示例: ["PENDING", "RUNNING", "SUCCEEDED", "FAILED"]
     * </p>
     */
    private java.util.List<String> possibleValues;
    
    /**
     * 是否为终止状态（到达此状态后任务不再继续）
     */
    @Builder.Default
    private boolean terminal = false;
}
