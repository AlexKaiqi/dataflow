package com.tencent.dataflow.domain.task.command;

import com.tencent.dataflow.domain.task.VariableDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * CreateTaskVersionCommand - 创建任务版本命令（创建新的草稿版本）
 * <p>
 * 对应 API: POST /api/v1/task-definitions/{namespace:name}/drafts
 * 创建新的草稿版本，可以基于最新草稿、指定草稿或已发布版本
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskVersionCommand {
    
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
     * 基于的版本号（可选，默认为最新草稿版本）
     * 可以是草稿版本或已发布版本
     */
    private String basedOn;
    
    /**
     * 输入变量定义列表
     */
    @Valid
    private List<VariableDefinition> inputVariables;
    
    /**
     * 输出变量定义列表
     */
    @Valid
    private List<VariableDefinition> outputVariables;
    
    /**
     * 执行配置
     */
    private Map<String, Object> executionConfig;
    
    /**
     * 任务描述（可选）
     */
    private String description;
}
