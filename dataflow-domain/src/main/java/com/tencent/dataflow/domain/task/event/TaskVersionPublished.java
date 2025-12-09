package com.tencent.dataflow.domain.task.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TaskVersionPublished - 任务版本已发布事件
 * <p>
 * 当草稿版本发布为正式版本时发布此事件
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskVersionPublished {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    @Builder.Default
    private String eventType = "TaskVersionPublished";
    
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
         * 发布的版本号（语义化版本）
         */
        private String publishedVersion;
        
        /**
         * 基于的草稿版本号
         */
        private String draftVersion;
        
        /**
         * 发布说明
         */
        private String releaseNotes;
        
        /**
         * 发布者
         */
        private String publishedBy;
    }
}
