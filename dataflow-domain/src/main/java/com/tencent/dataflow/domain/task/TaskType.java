package com.tencent.dataflow.domain.task;

/**
 * TaskType - 任务类型枚举
 * <p>
 * 不同的任务类型定义了不同的行为能力、事件集合和变量特征
 * </p>
 * 
 * @author dataflow
 */
public enum TaskType {
    
    /**
     * PySpark 批处理任务
     * 行为: start, retry
     * 事件: started, completed, failed
     */
    PYSPARK_OPERATOR("PySpark批处理任务"),
    
    /**
     * SQL 批处理任务
     * 行为: start, retry
     * 事件: started, completed, failed
     */
    SQL_OPERATOR("SQL批处理任务"),
    
    /**
     * Ray 分布式计算任务
     * 行为: start, retry
     * 事件: started, completed, failed
     */
    RAY_OPERATOR("Ray分布式计算任务"),
    
    /**
     * 流处理任务
     * 行为: start, stop, restart, retry
     * 事件: started, stopped, restarted, completed, failed
     */
    STREAMING_OPERATOR("流处理任务"),
    
    /**
     * 审批任务
     * 行为: start
     * 事件: started, approved, rejected, timeout
     */
    APPROVAL("审批任务"),
    
    /**
     * 模型推理任务
     * 行为: start, retry
     * 事件: started, completed, failed
     */
    MODEL_INFERENCE("模型推理任务");
    
    private final String description;
    
    TaskType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断该任务类型是否支持停止操作
     */
    public boolean supportsStop() {
        return this == STREAMING_OPERATOR;
    }
    
    /**
     * 判断该任务类型是否支持重启操作
     */
    public boolean supportsRestart() {
        return this == STREAMING_OPERATOR;
    }
    
    /**
     * 判断该任务类型是否支持重试操作
     */
    public boolean supportsRetry() {
        return this != APPROVAL;
    }
    
    /**
     * 判断是否为批处理任务
     */
    public boolean isBatchTask() {
        return this == PYSPARK_OPERATOR || this == SQL_OPERATOR 
            || this == RAY_OPERATOR || this == MODEL_INFERENCE;
    }
    
    /**
     * 判断是否为流处理任务
     */
    public boolean isStreamingTask() {
        return this == STREAMING_OPERATOR;
    }
}
