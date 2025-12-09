package com.tencent.dataflow.domain.task.repository;

import com.tencent.dataflow.domain.task.TaskDefinition;
import com.tencent.dataflow.domain.task.TaskVersion;

import java.util.List;
import java.util.Optional;

/**
 * TaskDefinitionRepository - 任务定义仓储接口
 * <p>
 * 负责任务定义的持久化操作
 * </p>
 * 
 * @author dataflow
 */
public interface TaskDefinitionRepository {
    
    /**
     * 保存任务定义
     */
    void save(TaskDefinition taskDefinition);
    
    /**
     * 根据命名空间和名称查找任务定义
     */
    Optional<TaskDefinition> findByNamespaceAndName(String namespace, String name);
    
    /**
     * 根据复合键查找特定版本
     */
    Optional<TaskVersion> findByCompositeKey(String namespace, String name, String version);
    
    /**
     * 查找命名空间下的所有任务定义
     */
    List<TaskDefinition> findByNamespace(String namespace);
    
    /**
     * 查找所有任务定义
     */
    List<TaskDefinition> findAll();
    
    /**
     * 删除任务定义
     */
    void delete(String namespace, String name);
    
    /**
     * 检查任务定义是否存在
     */
    boolean exists(String namespace, String name);
    
    /**
     * 检查特定版本是否被流水线引用
     */
    boolean isVersionReferenced(String namespace, String name, String version);
}
