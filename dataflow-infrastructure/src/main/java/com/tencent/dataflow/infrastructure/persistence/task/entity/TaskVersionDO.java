package com.tencent.dataflow.infrastructure.persistence.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TaskVersionDO - 任务版本数据对象
 * 
 * @author dataflow
 */
@Data
@TableName(value = "task_version", autoResultMap = true)
public class TaskVersionDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 任务定义ID（外键）
     */
    private Long taskDefinitionId;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 版本状态
     */
    private String status;
    
    /**
     * 输入变量定义（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> inputVariables;
    
    /**
     * 输出变量定义（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> outputVariables;
    
    /**
     * 执行配置（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> executionConfig;
    
    /**
     * 发布说明
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
     * 逻辑删除标记
     */
    private Boolean deleted;
}
