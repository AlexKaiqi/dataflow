package com.tencent.dataflow.domain.taskschema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EventDefinition - 事件定义（值对象）
 * <p>
 * 定义任务执行过程中可能产生的事件
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDefinition {

    // 标准 Event 名称常量
    public static final String EVENT_STARTED = "started";
    public static final String EVENT_SUCCEEDED = "succeeded";
    public static final String EVENT_FAILED = "failed";
    public static final String EVENT_STOPPED = "stopped";
    public static final String EVENT_PAUSED = "paused";
    public static final String EVENT_RESUMED = "resumed";
    
    /**
     * 事件名称（如 started, completed, failed）
     */
    private String name;
    
    /**
     * 事件描述
     */
    private String description;
    
    /**
     * 验证事件定义的有效性
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Event name cannot be empty");
        }
    }
}
