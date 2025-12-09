package com.tencent.dataflow.infrastructure.persistence.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * TaskDefinitionMapper - 任务定义Mapper
 * 
 * @author dataflow
 */
@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinitionDO> {
}
