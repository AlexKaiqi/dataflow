package com.tencent.dataflow.infrastructure.persistence.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskVersionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * TaskVersionMapper - 任务版本Mapper
 * 
 * @author dataflow
 */
@Mapper
public interface TaskVersionMapper extends BaseMapper<TaskVersionDO> {
}
