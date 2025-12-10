package com.tencent.dataflow.domain.task;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TaskVersion - 任务版本
 * <p>
 * 任务定义的某个特定版本，包含草稿版本和已发布版本
 * </p>
 * 
 * @author dataflow
 */
@Data
public class TaskVersion {
    
    /**
     * 版本号
     * 草稿版本: draft-YYYYMMDDHHmmss
     * 已发布版本: major.minor.patch (如 1.0.0)
     */
    private String version;
    
    /**
     * 版本状态
     */
    private VersionStatus status;
    
    /**
     * 输入变量定义列表
     */
    private List<VariableDefinition> inputVariables = new ArrayList<>();
    
    /**
     * 输出变量定义列表
     */
    private List<VariableDefinition> outputVariables = new ArrayList<>();
    
    /**
     * 执行配置（根据任务类型不同而不同）
     * 存储为 Map，实际使用时根据 TaskType 解析为具体的配置对象
     */
    private Map<String, Object> executionConfig;
    
    /**
     * 发布说明（仅已发布版本）
     */
    private String releaseNotes;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 创建者
     */
    private String createdBy;
    
    /**
     * 创建草稿版本
     */
    public static TaskVersion createDraft(TaskType taskType, String createdBy) {
        TaskVersion version = new TaskVersion();
        version.version = generateDraftVersion();
        version.status = VersionStatus.DRAFT;
        version.createdAt = Instant.now();
        version.createdBy = createdBy;
        
        // 根据任务类型初始化默认的输入输出变量
        initializeDefaultVariables(version, taskType);
        
        return version;
    }
    
    /**
     * 生成草稿版本号
     */
    private static String generateDraftVersion() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return "draft-" + now.format(formatter);
    }
    
    /**
     * 根据任务类型初始化默认变量
     */
    private static void initializeDefaultVariables(TaskVersion version, TaskType taskType) {
        // 所有批处理任务都有一些通用的输出变量
        if (taskType.isBatchTask()) {
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("execution_time")
                    .type(VariableType.NUMBER)
                    .description("任务执行时间（秒）")
                    .required(false)
                    .build()
            );
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("status")
                    .type(VariableType.STRING)
                    .description("任务执行状态")
                    .required(false)
                    .build()
            );
        }
        
        // 流处理任务的通用变量
        if (taskType.isStreamingTask()) {
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("processed_records")
                    .type(VariableType.NUMBER)
                    .description("已处理记录数")
                    .required(false)
                    .build()
            );
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("current_offset")
                    .type(VariableType.STRING)
                    .description("当前消费偏移量")
                    .required(false)
                    .build()
            );
        }
        
        // 审批任务的特定变量
        if (taskType == TaskType.APPROVAL) {
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("approver")
                    .type(VariableType.STRING)
                    .description("审批人")
                    .required(false)
                    .build()
            );
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("approval_time")
                    .type(VariableType.STRING)
                    .description("审批时间")
                    .required(false)
                    .build()
            );
            version.outputVariables.add(
                VariableDefinition.builder()
                    .name("comments")
                    .type(VariableType.STRING)
                    .description("审批意见")
                    .required(false)
                    .build()
            );
        }
    }
    
    /**
     * 更新执行配置
     * 注意：按照不可变设计，应该通过创建新版本来"修改"
     * 此方法仅用于版本创建过程中的初始化
     */
    public void updateExecutionConfig(Map<String, Object> config) {
        if (status == VersionStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot modify published version");
        }
        this.executionConfig = config;
    }
    
    /**
     * 检查是否为草稿版本
     */
    public boolean isDraft() {
        return status == VersionStatus.DRAFT;
    }
    
    /**
     * 检查是否为已发布版本
     */
    public boolean isPublished() {
        return status == VersionStatus.PUBLISHED;
    }
}
