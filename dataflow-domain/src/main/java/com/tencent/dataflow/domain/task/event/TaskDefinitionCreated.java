package com.tencent.dataflow.domain.task.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TaskDefinitionCreated - 任务定义已创建事件
 * <p>
 * 当创建新的任务定义时发布此事件
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDefinitionCreated {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    @Builder.Default
    private String eventType = "TaskDefinitionCreated";
    
    /**
     * 事件时间戳
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * 聚合根ID (namespace:name)
     */
    private String aggregateId;
    
    /**
     * 版本号
     */
    private int version;
    
    /**
     * 事件负载
     */
    private Payload payload;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        /**
         * 命名空间
         */
        private String namespace;
        
        /**
         * 任务名称
         */
        private String name;
        
        /**
         * 初始草稿版本号
         */
        private String initialVersion;
        
        /**
         * 任务类型
         */
        private String type;
        
        /**
         * 创建者
         */
        private String createdBy;
        
        /**
         * 任务描述
         */
        private String description;
    }
}
