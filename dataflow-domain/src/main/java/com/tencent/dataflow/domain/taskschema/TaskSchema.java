package com.tencent.dataflow.domain.taskschema;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TaskSchema - 任务元定义（聚合根）
 * <p>
 * 定义一类任务的能力：支持哪些行为、产生哪些事件、包含哪些状态。
 * </p>
 * 
 * @author dataflow
 */
@Data
public class TaskSchema {
    
    /**
     * Schema 类型标识（全局唯一）
     */
    private String type;
    
    /**
     * Schema 描述
     */
    private String description;
    
    /**
     * 支持的行为列表（key: action name, value: action definition）
     */
    private Map<String, ActionDefinition> actions = new HashMap<>();
    
    /**
     * 产生的事件列表
     */
    private List<EventDefinition> events = new ArrayList<>();
    
    /**
     * 状态定义列表（key: state name, value: state definition）
     */
    private Map<String, StateDefinition> states = new HashMap<>();
    
    /**
     * 执行器配置
     */
    private ExecutorConfig executor;
    
    /**
     * 执行配置的 JSON Schema
     */
    private Map<String, Object> executionConfigSchema;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 创建者
     */
    private String createdBy;
    
    /**
     * 是否已删除（软删除）
     */
    private boolean deleted = false;
}
