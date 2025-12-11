package com.tencent.dataflow.domain.taskschema;

/**
 * AccessProtocol - 访问协议枚举
 * <p>
 * 定义如何访问任务的行为（Action）或状态（State）。
 * </p>
 */
public enum AccessProtocol {
    /**
     * 标准 HTTP REST 请求 (默认)
     * Endpoint 格式: URL Path (如 "/start", "/status")
     */
    HTTP,

    /**
     * gRPC 远程调用
     * Endpoint 格式: Service/Method (如 "JobService/Start")
     */
    GRPC,

    /**
     * 内部方法调用 (In-Process)
     * 适用于任务逻辑与控制流服务在同一进程或通过本地 Bean 调用的场景。
     * Endpoint 格式: BeanName/MethodName (如 "sparkJobHandler/start")
     */
    INTERNAL,
    
    /**
     * Kubernetes API 操作
     * 适用于直接操作 K8S CRD 的场景。
     * Endpoint 格式: Group/Version/Kind (如 "batch/v1/Job")
     */
    K8S
}
