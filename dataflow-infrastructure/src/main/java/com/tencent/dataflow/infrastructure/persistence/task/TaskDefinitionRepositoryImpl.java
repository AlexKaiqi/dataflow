package com.tencent.dataflow.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tencent.dataflow.domain.task.TaskDefinition;
import com.tencent.dataflow.domain.task.TaskVersion;
import com.tencent.dataflow.domain.task.repository.TaskDefinitionRepository;
import com.tencent.dataflow.infrastructure.persistence.task.converter.TaskDefinitionConverter;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskDefinitionDO;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskVersionDO;
import com.tencent.dataflow.infrastructure.persistence.task.mapper.TaskDefinitionMapper;
import com.tencent.dataflow.infrastructure.persistence.task.mapper.TaskVersionMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * TaskDefinitionRepositoryImpl - 任务定义仓储实现
 * <p>
 * 使用 @Validated 注解启用方法参数校验
 * </p>
 * 
 * @author dataflow
 */
@Repository
@Validated
public class TaskDefinitionRepositoryImpl implements TaskDefinitionRepository {
    
    private final TaskDefinitionMapper taskDefinitionMapper;
    private final TaskVersionMapper taskVersionMapper;
    
    public TaskDefinitionRepositoryImpl(TaskDefinitionMapper taskDefinitionMapper,
                                       TaskVersionMapper taskVersionMapper) {
        this.taskDefinitionMapper = taskDefinitionMapper;
        this.taskVersionMapper = taskVersionMapper;
    }
    
    @Override
    @Transactional
    public void save(TaskDefinition taskDefinition) {
        // 查询或插入任务定义
        TaskDefinitionDO taskDefDO = taskDefinitionMapper.selectOne(
            new LambdaQueryWrapper<TaskDefinitionDO>()
                .eq(TaskDefinitionDO::getNamespace, taskDefinition.getNamespace())
                .eq(TaskDefinitionDO::getName, taskDefinition.getName())
        );
        
        if (taskDefDO == null) {
            // 新建任务定义
            taskDefDO = TaskDefinitionConverter.toDataObject(taskDefinition);
            taskDefinitionMapper.insert(taskDefDO);
        } else {
            // 更新任务定义
            taskDefDO.setType(taskDefinition.getType().name());
            taskDefDO.setDescription(taskDefinition.getDescription());
            taskDefinitionMapper.updateById(taskDefDO);
        }
        
        // 保存所有版本
        String namespace = taskDefinition.getNamespace();
        String name = taskDefinition.getName();
        for (TaskVersion version : taskDefinition.getVersions()) {
            // 检查版本是否已存在
            TaskVersionDO existingVersion = taskVersionMapper.selectOne(
                new LambdaQueryWrapper<TaskVersionDO>()
                    .eq(TaskVersionDO::getNamespace, namespace)
                    .eq(TaskVersionDO::getName, name)
                    .eq(TaskVersionDO::getVersion, version.getVersion())
            );
            
            TaskVersionDO versionDO = TaskDefinitionConverter.versionToDataObject(version, namespace, name);
            
            if (existingVersion == null) {
                // 新建版本
                taskVersionMapper.insert(versionDO);
            } else {
                // 更新版本（理论上已发布版本不应该更新，但草稿版本可能需要）
                versionDO.setId(existingVersion.getId());
                taskVersionMapper.updateById(versionDO);
            }
        }
    }
    
    @Override
    public Optional<TaskDefinition> findByNamespaceAndName(String namespace, String name) {
        TaskDefinitionDO taskDefDO = taskDefinitionMapper.selectOne(
            new LambdaQueryWrapper<TaskDefinitionDO>()
                .eq(TaskDefinitionDO::getNamespace, namespace)
                .eq(TaskDefinitionDO::getName, name)
        );
        
        if (taskDefDO == null) {
            return Optional.empty();
        }
        
        // 查询所有版本
        List<TaskVersionDO> versionDOs = taskVersionMapper.selectList(
            new LambdaQueryWrapper<TaskVersionDO>()
                .eq(TaskVersionDO::getNamespace, namespace)
                .eq(TaskVersionDO::getName, name)
                .orderByDesc(TaskVersionDO::getCreatedAt)
        );
        
        TaskDefinition taskDef = TaskDefinitionConverter.toDomain(taskDefDO, versionDOs);
        return Optional.of(taskDef);
    }
    
    @Override
    public Optional<TaskVersion> findByCompositeKey(String namespace, String name, String version) {
        // 直接通过 namespace+name+version 查询，单次查询
        TaskVersionDO versionDO = taskVersionMapper.selectOne(
            new LambdaQueryWrapper<TaskVersionDO>()
                .eq(TaskVersionDO::getNamespace, namespace)
                .eq(TaskVersionDO::getName, name)
                .eq(TaskVersionDO::getVersion, version)
        );
        
        if (versionDO == null) {
            return Optional.empty();
        }
        
        return Optional.of(TaskDefinitionConverter.versionToDomain(versionDO));
    }
    
    @Override
    public List<TaskDefinition> findByNamespace(String namespace) {
        List<TaskDefinitionDO> taskDefDOs = taskDefinitionMapper.selectList(
            new LambdaQueryWrapper<TaskDefinitionDO>()
                .eq(TaskDefinitionDO::getNamespace, namespace)
        );
        
        return taskDefDOs.stream()
            .map(taskDefDO -> {
                List<TaskVersionDO> versionDOs = taskVersionMapper.selectList(
                    new LambdaQueryWrapper<TaskVersionDO>()
                        .eq(TaskVersionDO::getNamespace, taskDefDO.getNamespace())
                        .eq(TaskVersionDO::getName, taskDefDO.getName())
                        .orderByDesc(TaskVersionDO::getCreatedAt)
                );
                return TaskDefinitionConverter.toDomain(taskDefDO, versionDOs);
            })
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(String namespace, String name) {
        TaskDefinitionDO taskDefDO = taskDefinitionMapper.selectOne(
            new LambdaQueryWrapper<TaskDefinitionDO>()
                .eq(TaskDefinitionDO::getNamespace, namespace)
                .eq(TaskDefinitionDO::getName, name)
        );
        
        if (taskDefDO != null) {
            // 删除所有版本
            taskVersionMapper.delete(
                new LambdaQueryWrapper<TaskVersionDO>()
                    .eq(TaskVersionDO::getNamespace, namespace)
                    .eq(TaskVersionDO::getName, name)
            );
            
            // 删除任务定义（MyBatis-Plus 需要 id）
            taskDefinitionMapper.deleteById(taskDefDO.getId());
        }
    }
    
    @Override
    public boolean exists(String namespace, String name) {
        Long count = taskDefinitionMapper.selectCount(
            new LambdaQueryWrapper<TaskDefinitionDO>()
                .eq(TaskDefinitionDO::getNamespace, namespace)
                .eq(TaskDefinitionDO::getName, name)
        );
        return count != null && count > 0;
    }
    
    @Override
    public boolean isVersionReferenced(String namespace, String name, String version) {
        // TODO: 实现检查版本是否被 Pipeline 引用的逻辑
        // 这里暂时返回 false，实际应该查询 pipeline 表
        return false;
    }
}
