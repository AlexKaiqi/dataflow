package com.tencent.dataflow.infrastructure.persistence.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * TaskDefinitionDO - 任务定义数据对象
 * 
 * 注意：id 字段仅用于基础设施层的数据库操作，不应暴露到领域层
 * 领域层使用 (namespace, name) 作为业务标识
 * 
 * @author dataflow
 */
@Data
@TableName(value = "task_definition", autoResultMap = true)
public class TaskDefinitionDO {
    
    /**
     * 主键ID（仅用于基础设施层，不暴露到领域层）
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
