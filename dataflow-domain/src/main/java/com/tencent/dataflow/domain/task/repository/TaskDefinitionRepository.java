package com.tencent.dataflow.domain.task.repository;

import com.tencent.dataflow.domain.task.TaskDefinition;
import com.tencent.dataflow.domain.task.TaskVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
     * 
     * @param taskDefinition 任务定义，不能为空
     * @throws IllegalArgumentException 如果参数为空
     */
    void save(@NotNull TaskDefinition taskDefinition);
    
    /**
     * 根据命名空间和名称查找任务定义
     * 
     * @param namespace 命名空间，不能为空或空白
     * @param name 任务名称，不能为空或空白
     * @return 任务定义的 Optional
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    Optional<TaskDefinition> findByNamespaceAndName(@NotBlank String namespace, @NotBlank String name);
    
    /**
     * 根据复合键查找特定版本
     * 
     * @param namespace 命名空间，不能为空或空白
     * @param name 任务名称，不能为空或空白
     * @param version 版本号，不能为空或空白
     * @return 任务版本的 Optional
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    Optional<TaskVersion> findByCompositeKey(@NotBlank String namespace, @NotBlank String name, @NotBlank String version);
    
    /**
     * 查找命名空间下的所有任务定义
     * 
     * @param namespace 命名空间，不能为空或空白
     * @return 任务定义列表
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    List<TaskDefinition> findByNamespace(@NotBlank String namespace);

    /**
     * 删除任务定义
     * 
     * @param namespace 命名空间，不能为空或空白
     * @param name 任务名称，不能为空或空白
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    void delete(@NotBlank String namespace, @NotBlank String name);
    
    /**
     * 检查任务定义是否存在
     * 
     * @param namespace 命名空间，不能为空或空白
     * @param name 任务名称，不能为空或空白
     * @return 是否存在
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    boolean exists(@NotBlank String namespace, @NotBlank String name);
    
    /**
     * 检查特定版本是否被流水线引用
     * 
     * @param namespace 命名空间，不能为空或空白
     * @param name 任务名称，不能为空或空白
     * @param version 版本号，不能为空或空白
     * @return 是否被引用
     * @throws IllegalArgumentException 如果参数为空或空白
     */
    boolean isVersionReferenced(@NotBlank String namespace, @NotBlank String name, @NotBlank String version);
}

