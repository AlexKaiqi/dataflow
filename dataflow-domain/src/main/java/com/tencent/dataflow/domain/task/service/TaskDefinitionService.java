package com.tencent.dataflow.domain.task.service;

import com.tencent.dataflow.domain.task.*;
import com.tencent.dataflow.domain.task.event.TaskDefinitionCreated;
import com.tencent.dataflow.domain.task.event.TaskVersionCreated;
import com.tencent.dataflow.domain.task.event.TaskVersionPublished;
import com.tencent.dataflow.domain.task.repository.TaskDefinitionRepository;

import java.util.UUID;

/**
 * TaskDefinitionService - 任务定义领域服务
 * <p>
 * 提供任务定义相关的业务操作
 * </p>
 * 
 * @author dataflow
 */
public class TaskDefinitionService {
    
    private final TaskDefinitionRepository repository;
    
    public TaskDefinitionService(TaskDefinitionRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 创建新的任务定义
     */
    public TaskDefinition createTaskDefinition(
            String namespace, 
            String name, 
            TaskType type,
            String description,
            String createdBy) {
        
        // 检查是否已存在
        if (repository.exists(namespace, name)) {
            throw new IllegalArgumentException(
                "Task definition already exists: " + namespace + ":" + name);
        }
        
        // 创建任务定义（自动创建初始草稿版本）
        TaskDefinition taskDef = TaskDefinition.create(
            namespace, name, type, description, createdBy);
        
        // 保存到仓储
        repository.save(taskDef);
        
        // 发布领域事件
        publishTaskDefinitionCreated(taskDef);
        
        return taskDef;
    }
    
    /**
     * 为任务定义创建新的草稿版本
     */
    public TaskVersion createDraftVersion(
            String namespace, 
            String name, 
            String createdBy) {
        
        TaskDefinition taskDef = repository.findByNamespaceAndName(namespace, name)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task definition not found: " + namespace + ":" + name));
        
        TaskVersion latestDraft = taskDef.getLatestDraftVersion();
        if (latestDraft == null) {
            throw new IllegalStateException("No draft version found");
        }
        
        // 创建新草稿版本
        TaskVersion newVersion = taskDef.createNewDraftVersion(createdBy);
        
        // 保存
        repository.save(taskDef);
        
        // 发布领域事件
        publishTaskVersionCreated(taskDef, newVersion, latestDraft.getVersion());
        
        return newVersion;
    }
    
    /**
     * 发布草稿版本
     */
    public void publishVersion(
            String namespace,
            String name,
            String draftVersion,
            String semanticVersion,
            String releaseNotes,
            String publishedBy) {
        
        TaskDefinition taskDef = repository.findByNamespaceAndName(namespace, name)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task definition not found: " + namespace + ":" + name));
        
        // 发布版本
        taskDef.publishVersion(draftVersion, semanticVersion, releaseNotes, publishedBy);
        
        // 保存
        repository.save(taskDef);
        
        // 发布领域事件
        publishTaskVersionPublished(taskDef, draftVersion, semanticVersion, releaseNotes, publishedBy);
    }
    
    /**
     * 为草稿版本添加输入变量
     */
    public void addInputVariable(
            String namespace,
            String name,
            String version,
            VariableDefinition variable) {
        
        TaskVersion taskVersion = repository.findByCompositeKey(namespace, name, version)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task version not found: " + namespace + ":" + name + ":" + version));
        
        // 验证变量定义
        variable.validate();
        
        // 添加输入变量
        taskVersion.addInputVariable(variable);
        
        // 保存
        TaskDefinition taskDef = repository.findByNamespaceAndName(namespace, name)
            .orElseThrow(() -> new IllegalStateException("Task definition not found"));
        repository.save(taskDef);
    }
    
    /**
     * 为草稿版本添加输出变量
     */
    public void addOutputVariable(
            String namespace,
            String name,
            String version,
            VariableDefinition variable) {
        
        TaskVersion taskVersion = repository.findByCompositeKey(namespace, name, version)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task version not found: " + namespace + ":" + name + ":" + version));
        
        // 验证变量定义
        variable.validate();
        
        // 添加输出变量
        taskVersion.addOutputVariable(variable);
        
        // 保存
        TaskDefinition taskDef = repository.findByNamespaceAndName(namespace, name)
            .orElseThrow(() -> new IllegalStateException("Task definition not found"));
        repository.save(taskDef);
    }
    
    /**
     * 删除任务定义（检查引用约束）
     */
    public void deleteTaskDefinition(String namespace, String name) {
        TaskDefinition taskDef = repository.findByNamespaceAndName(namespace, name)
            .orElseThrow(() -> new IllegalArgumentException(
                "Task definition not found: " + namespace + ":" + name));
        
        // 检查是否有已发布版本被引用
        for (TaskVersion version : taskDef.getVersions()) {
            if (version.isPublished() && 
                repository.isVersionReferenced(namespace, name, version.getVersion())) {
                throw new IllegalStateException(
                    "Cannot delete task definition: version " + version.getVersion() + 
                    " is referenced by pipelines");
            }
        }
        
        // 删除
        repository.delete(namespace, name);
    }
    
    // ==================== 私有方法 ====================
    
    private void publishTaskDefinitionCreated(TaskDefinition taskDef) {
        TaskDefinitionCreated event = TaskDefinitionCreated.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(taskDef.getNamespace() + ":" + taskDef.getName())
            .version(1)
            .payload(TaskDefinitionCreated.Payload.builder()
                .namespace(taskDef.getNamespace())
                .name(taskDef.getName())
                .initialVersion(taskDef.getVersions().get(0).getVersion())
                .type(taskDef.getType().name())
                .createdBy(taskDef.getCreatedBy())
                .description(taskDef.getDescription())
                .build())
            .build();
        
        // TODO: 发布到事件总线
        // eventPublisher.publish(event);
    }
    
    private void publishTaskVersionCreated(TaskDefinition taskDef, TaskVersion newVersion, String basedOn) {
        TaskVersionCreated event = TaskVersionCreated.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(taskDef.getNamespace() + ":" + taskDef.getName())
            .version(taskDef.getVersions().size())
            .payload(TaskVersionCreated.Payload.builder()
                .namespace(taskDef.getNamespace())
                .name(taskDef.getName())
                .version(newVersion.getVersion())
                .basedOn(basedOn)
                .createdBy(newVersion.getCreatedBy())
                .build())
            .build();
        
        // TODO: 发布到事件总线
        // eventPublisher.publish(event);
    }
    
    private void publishTaskVersionPublished(
            TaskDefinition taskDef, 
            String draftVersion, 
            String publishedVersion,
            String releaseNotes,
            String publishedBy) {
        
        TaskVersionPublished event = TaskVersionPublished.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(taskDef.getNamespace() + ":" + taskDef.getName())
            .version(taskDef.getVersions().size())
            .payload(TaskVersionPublished.Payload.builder()
                .namespace(taskDef.getNamespace())
                .name(taskDef.getName())
                .publishedVersion(publishedVersion)
                .draftVersion(draftVersion)
                .releaseNotes(releaseNotes)
                .publishedBy(publishedBy)
                .build())
            .build();
        
        // TODO: 发布到事件总线
        // eventPublisher.publish(event);
    }
}
