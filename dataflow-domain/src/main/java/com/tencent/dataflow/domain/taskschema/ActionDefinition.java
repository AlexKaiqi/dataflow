package com.tencent.dataflow.domain.taskschema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ActionDefinition - 行为定义（值对象）
 * <p>
 * 定义任务支持的某个行为（如 start, pause, resume, stop）
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinition {

    // 标准 Action 名称常量
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_RESTART = "restart";
    public static final String ACTION_RETRY = "retry";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    
    /**
     * 行为名称（如 start, pause, resume, stop）
     */
    private String name;
    
    /**
     * 行为描述
     */
    private String description;

    /**
     * 访问协议
     * <p>
     * 定义如何触发该行为。默认为 HTTP。
     * </p>
     */
    @Builder.Default
    private AccessProtocol protocol = AccessProtocol.HTTP;
    
    /**
     * 访问端点/选择器
     * <p>
     * 根据协议不同，含义不同：
     * - HTTP: URL 路径 (支持 {executionId} 等占位符)。若为空，默认为 "/{name}" (POST)。
     * - GRPC: Method Name。若为空，默认为 "{name}"。
     * - INTERNAL: BeanName 或 MethodName。
     * </p>
     */
    private String endpoint;

    /**
     * 协议扩展配置
     * <p>
     * 针对特定协议的额外参数。
     * 例如 HTTP 方法 (method: "PUT")，或者 GRPC 的 Service Name。
     * </p>
     */
    private java.util.Map<String, Object> protocolConfig;
}
