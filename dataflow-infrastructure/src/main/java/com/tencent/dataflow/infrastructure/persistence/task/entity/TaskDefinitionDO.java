package com.tencent.dataflow.infrastructure.persistence.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * TaskDefinitionDO - 任务定义数据对象
 * 
 * @author dataflow
 */
@Data
@TableName(value = "task_definition", autoResultMap = true)
public class TaskDefinitionDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 命名空间
     */
    private String namespace;
    
    /**
     * 任务名称
     */
    private String name;
    
    /**
     * 任务类型
     */
    private String type;
    
    /**
     * 任务描述
     */
    private String description;
    
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
