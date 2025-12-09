package com.tencent.dataflow.domain.task.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PublishTaskVersionCommand - 发布任务版本命令
 * <p>
 * 对应 API: POST /api/v1/task-definitions/{namespace:name}/drafts/{draft-version}/publish
 * 将指定的草稿版本发布为正式版本，发布后该版本变为不可变
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishTaskVersionCommand {
    
    /**
     * 命名空间
     */
    @NotBlank(message = "命名空间不能为空")
    private String namespace;
    
    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;
    
    /**
     * 草稿版本号，如 "draft-20250115140000"
     */
    @NotBlank(message = "草稿版本号不能为空")
    private String draftVersion;
    
    /**
     * 语义化版本号 (major.minor.patch)，如 "1.0.0"
     */
    @NotBlank(message = "语义化版本号不能为空")
    @Pattern(regexp = "\\d+\\.\\d+\\.\\d+", message = "版本号格式必须为 major.minor.patch")
    private String version;
    
    /**
     * 发布说明
     */
    private String releaseNotes;
}
