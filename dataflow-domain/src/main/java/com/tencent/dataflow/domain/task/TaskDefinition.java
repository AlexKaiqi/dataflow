package com.tencent.dataflow.domain.task;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskDefinition - 任务定义（聚合根）
 * <p>
 * 可复用的任务模板，定义了一类任务"能做什么"、"需要什么输入"、"产生什么输出"。
 * 支持版本管理，允许同一任务有多个版本，支持灰度发布和回滚。
 * </p>
 * 
 * @author dataflow
 */
@Data
public class TaskDefinition {
    
    /**
     * 命名空间，如 "com.company.tasks"
     */
    private String namespace;
    
    /**
     * 任务名称，如 "data_transform"
     */
    private String name;
    
    /**
     * 任务类型
     */
    private TaskType type;
    
    /**
     * 任务描述
     */
    private String description;
    
    /**
     * 版本列表（按时间倒序）
     */
    private List<TaskVersion> versions = new ArrayList<>();
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 创建者
     */
    private String createdBy;
    
    /**
     * 获取复合键（全局唯一标识）
     */
    public String getCompositeKey(String version) {
        return namespace + ":" + name + ":" + version;
    }
    
    /**
     * 创建新的任务定义（自动创建初始草稿版本）
     */
    public static TaskDefinition create(String namespace, String name, TaskType type, 
                                       String description, String createdBy) {
        TaskDefinition task = new TaskDefinition();
        task.namespace = namespace;
        task.name = name;
        task.type = type;
        task.description = description;
        task.createdBy = createdBy;
        task.createdAt = Instant.now();
        
        // 自动创建初始草稿版本
        TaskVersion initialVersion = TaskVersion.createDraft(type);
        task.versions.add(initialVersion);
        
        return task;
    }
    
    /**
     * 创建新的草稿版本
     */
    public TaskVersion createNewDraftVersion(String createdBy) {
        TaskVersion latestDraft = getLatestDraftVersion();
        if (latestDraft == null) {
            throw new IllegalStateException("No draft version found to base on");
        }
        
        // 基于最新草稿创建新版本
        TaskVersion newVersion = TaskVersion.createDraft(type);
        newVersion.setInputVariables(new ArrayList<>(latestDraft.getInputVariables()));
        newVersion.setOutputVariables(new ArrayList<>(latestDraft.getOutputVariables()));
        newVersion.setExecutionConfig(latestDraft.getExecutionConfig());
        newVersion.setLastModifiedBy(createdBy);
        
        versions.add(0, newVersion); // 添加到列表头部
        return newVersion;
    }
    
    /**
     * 发布草稿版本
     */
    public void publishVersion(String draftVersion, String semanticVersion, 
                              String releaseNotes, String publishedBy) {
        TaskVersion draft = getVersion(draftVersion);
        if (draft == null) {
            throw new IllegalArgumentException("Draft version not found: " + draftVersion);
        }
        
        if (draft.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only draft versions can be published");
        }
        
        // 验证语义化版本号
        validateSemanticVersion(semanticVersion);
        
        // 创建已发布版本
        TaskVersion publishedVersion = new TaskVersion();
        publishedVersion.setVersion(semanticVersion);
        publishedVersion.setStatus(VersionStatus.PUBLISHED);
        publishedVersion.setInputVariables(new ArrayList<>(draft.getInputVariables()));
        publishedVersion.setOutputVariables(new ArrayList<>(draft.getOutputVariables()));
        publishedVersion.setExecutionConfig(draft.getExecutionConfig());
        publishedVersion.setReleaseNotes(releaseNotes);
        publishedVersion.setCreatedAt(Instant.now());
        publishedVersion.setCreatedBy(publishedBy);
        
        versions.add(publishedVersion);
    }
    
    /**
     * 获取最新的草稿版本
     */
    public TaskVersion getLatestDraftVersion() {
        return versions.stream()
                .filter(v -> v.getStatus() == VersionStatus.DRAFT)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取指定版本
     */
    public TaskVersion getVersion(String version) {
        return versions.stream()
                .filter(v -> v.getVersion().equals(version))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取最新的已发布版本
     */
    public TaskVersion getLatestPublishedVersion() {
        return versions.stream()
                .filter(v -> v.getStatus() == VersionStatus.PUBLISHED)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 验证语义化版本号格式
     */
    private void validateSemanticVersion(String version) {
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException(
                "Invalid semantic version format: " + version + ". Expected: major.minor.patch");
        }
        
        // 确保版本号递增
        String latestPublished = getLatestPublishedVersion() != null 
            ? getLatestPublishedVersion().getVersion() 
            : "0.0.0";
        
        if (compareVersions(version, latestPublished) <= 0) {
            throw new IllegalArgumentException(
                "New version " + version + " must be greater than latest published version " + latestPublished);
        }
    }
    
    /**
     * 比较两个语义化版本号
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        for (int i = 0; i < 3; i++) {
            int num1 = Integer.parseInt(parts1[i]);
            int num2 = Integer.parseInt(parts2[i]);
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
}
