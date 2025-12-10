package com.tencent.dataflow.infrastructure.persistence.task.converter;

import com.tencent.dataflow.domain.task.*;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskDefinitionDO;
import com.tencent.dataflow.infrastructure.persistence.task.entity.TaskVersionDO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TaskDefinitionConverter - 任务定义转换器
 * <p>
 * 负责领域对象与数据对象之间的转换
 * </p>
 * 
 * @author dataflow
 */
public class TaskDefinitionConverter {
    
    /**
     * 领域对象转数据对象
     */
    public static TaskDefinitionDO toDataObject(TaskDefinition domain) {
        if (domain == null) {
            return null;
        }
        
        TaskDefinitionDO dataObject = new TaskDefinitionDO();
        dataObject.setNamespace(domain.getNamespace());
        dataObject.setName(domain.getName());
        dataObject.setType(domain.getType().name());
        dataObject.setDescription(domain.getDescription());
        dataObject.setCreatedAt(domain.getCreatedAt());
        dataObject.setCreatedBy(domain.getCreatedBy());
        
        return dataObject;
    }
    
    /**
     * 数据对象转领域对象
     */
    public static TaskDefinition toDomain(TaskDefinitionDO dataObject, List<TaskVersionDO> versionDOs) {
        if (dataObject == null) {
            return null;
        }
        
        TaskDefinition domain = new TaskDefinition();
        domain.setNamespace(dataObject.getNamespace());
        domain.setName(dataObject.getName());
        domain.setType(TaskType.valueOf(dataObject.getType()));
        domain.setDescription(dataObject.getDescription());
        domain.setCreatedAt(dataObject.getCreatedAt());
        domain.setCreatedBy(dataObject.getCreatedBy());
        
        // 转换版本列表
        if (versionDOs != null) {
            List<TaskVersion> versions = versionDOs.stream()
                .map(TaskDefinitionConverter::versionToDomain)
                .collect(Collectors.toList());
            domain.setVersions(versions);
        }
        
        return domain;
    }
    
    /**
     * 版本领域对象转数据对象
     */
    public static TaskVersionDO versionToDataObject(TaskVersion domain, String namespace, String name) {
        if (domain == null) {
            return null;
        }
        
        TaskVersionDO dataObject = new TaskVersionDO();
        dataObject.setNamespace(namespace);
        dataObject.setName(name);
        dataObject.setVersion(domain.getVersion());
        dataObject.setStatus(domain.getStatus().name());
        
        // 转换输入变量
        if (domain.getInputVariables() != null) {
            dataObject.setInputVariables(
                domain.getInputVariables().stream()
                    .map(TaskDefinitionConverter::variableToMap)
                    .collect(Collectors.toList())
            );
        }
        
        // 转换输出变量
        if (domain.getOutputVariables() != null) {
            dataObject.setOutputVariables(
                domain.getOutputVariables().stream()
                    .map(TaskDefinitionConverter::variableToMap)
                    .collect(Collectors.toList())
            );
        }
        
        dataObject.setExecutionConfig(domain.getExecutionConfig());
        dataObject.setReleaseNotes(domain.getReleaseNotes());
        dataObject.setCreatedAt(domain.getCreatedAt());
        dataObject.setCreatedBy(domain.getCreatedBy());
        
        return dataObject;
    }
    
    /**
     * 版本数据对象转领域对象
     */
    public static TaskVersion versionToDomain(TaskVersionDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        
        TaskVersion domain = new TaskVersion();
        domain.setVersion(dataObject.getVersion());
        domain.setStatus(VersionStatus.valueOf(dataObject.getStatus()));
        
        // 转换输入变量
        if (dataObject.getInputVariables() != null) {
            domain.setInputVariables(
                dataObject.getInputVariables().stream()
                    .map(TaskDefinitionConverter::mapToVariable)
                    .collect(Collectors.toList())
            );
        } else {
            domain.setInputVariables(new ArrayList<>());
        }
        
        // 转换输出变量
        if (dataObject.getOutputVariables() != null) {
            domain.setOutputVariables(
                dataObject.getOutputVariables().stream()
                    .map(TaskDefinitionConverter::mapToVariable)
                    .collect(Collectors.toList())
            );
        } else {
            domain.setOutputVariables(new ArrayList<>());
        }
        
        domain.setExecutionConfig(dataObject.getExecutionConfig());
        domain.setReleaseNotes(dataObject.getReleaseNotes());
        domain.setCreatedAt(dataObject.getCreatedAt());
        domain.setCreatedBy(dataObject.getCreatedBy());
        
        return domain;
    }
    
    /**
     * 变量定义转Map
     */
    private static Map<String, Object> variableToMap(VariableDefinition variable) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", variable.getName());
        map.put("type", variable.getType().name());
        map.put("required", variable.isRequired());
        map.put("description", variable.getDescription());
        
        if (variable.getValidation() != null) {
            map.put("validation", validationToMap(variable.getValidation()));
        }
        
        if (variable.getDefaultValue() != null) {
            map.put("defaultValue", variable.getDefaultValue());
        }
        
        return map;
    }
    
    /**
     * Map转变量定义
     */
    @SuppressWarnings("unchecked")
    private static VariableDefinition mapToVariable(Map<String, Object> map) {
        VariableDefinition.VariableDefinitionBuilder builder = VariableDefinition.builder()
            .name((String) map.get("name"))
            .type(VariableType.valueOf((String) map.get("type")))
            .required((Boolean) map.getOrDefault("required", true))
            .description((String) map.get("description"));
        
        if (map.containsKey("validation")) {
            builder.validation(mapToValidation((Map<String, Object>) map.get("validation")));
        }
        
        if (map.containsKey("defaultValue")) {
            builder.defaultValue(map.get("defaultValue"));
        }
        
        return builder.build();
    }
    
    /**
     * 验证规则转Map
     */
    private static Map<String, Object> validationToMap(ValidationRule validation) {
        Map<String, Object> map = new HashMap<>();
        
        if (validation.getPattern() != null) {
            map.put("pattern", validation.getPattern());
        }
        if (validation.getMinLength() != null) {
            map.put("minLength", validation.getMinLength());
        }
        if (validation.getMaxLength() != null) {
            map.put("maxLength", validation.getMaxLength());
        }
        if (validation.getEnumValues() != null) {
            map.put("enumValues", validation.getEnumValues());
        }
        if (validation.getMinValue() != null) {
            map.put("minValue", validation.getMinValue());
        }
        if (validation.getMaxValue() != null) {
            map.put("maxValue", validation.getMaxValue());
        }
        
        return map;
    }
    
    /**
     * Map转验证规则
     */
    @SuppressWarnings("unchecked")
    private static ValidationRule mapToValidation(Map<String, Object> map) {
        ValidationRule.ValidationRuleBuilder builder = ValidationRule.builder();
        
        if (map.containsKey("pattern")) {
            builder.pattern((String) map.get("pattern"));
        }
        if (map.containsKey("minLength")) {
            builder.minLength(((Number) map.get("minLength")).intValue());
        }
        if (map.containsKey("maxLength")) {
            builder.maxLength(((Number) map.get("maxLength")).intValue());
        }
        if (map.containsKey("enumValues")) {
            builder.enumValues((List<Object>) map.get("enumValues"));
        }
        if (map.containsKey("minValue")) {
            builder.minValue(((Number) map.get("minValue")).doubleValue());
        }
        if (map.containsKey("maxValue")) {
            builder.maxValue(((Number) map.get("maxValue")).doubleValue());
        }
        
        return builder.build();
    }
}
