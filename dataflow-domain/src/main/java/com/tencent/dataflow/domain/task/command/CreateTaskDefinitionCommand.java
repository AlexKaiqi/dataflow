package com.tencent.dataflow.domain.task.command;

import com.tencent.dataflow.domain.task.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CreateTaskDefinitionCommand - 创建任务定义命令
 * <p>
 * 对应 API: POST /api/v1/task-definitions
 * 创建任务定义聚合根，系统自动创建初始草稿版本
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskDefinitionCommand {
    
    /**
     * 命名空间，如 "com.company.tasks"
     */
    @NotBlank(message = "命名空间不能为空")
    private String namespace;
    
    /**
     * 任务名称，如 "data_cleaner"
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;
    
    /**
     * 任务类型
     */
    @NotNull(message = "任务类型不能为空")
    private TaskType type;
    
    /**
     * 任务描述
     */
    @NotBlank(message = "任务描述不能为空")
    private String description;
}
