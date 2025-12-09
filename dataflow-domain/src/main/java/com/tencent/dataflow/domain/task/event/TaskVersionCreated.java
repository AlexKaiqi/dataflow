package com.tencent.dataflow.domain.task.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TaskVersionCreated - 任务版本已创建事件
 * <p>
 * 当创建新的草稿版本时发布此事件
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskVersionCreated {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    @Builder.Default
    private String eventType = "TaskVersionCreated";
    
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
         * 新版本号
         */
        private String version;
        
        /**
         * 基于哪个版本创建
         */
        private String basedOn;
        
        /**
         * 创建者
         */
        private String createdBy;
    }
}
