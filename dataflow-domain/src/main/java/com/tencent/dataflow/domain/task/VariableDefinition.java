package com.tencent.dataflow.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VariableDefinition - 变量定义（值对象）
 * <p>
 * 用于定义变量的元数据，声明"这个变量是什么"
 * 不涉及"如何获取"或"如何转换"（那是运行时的职责）
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableDefinition {
    
    /**
     * 变量名称，在同一上下文中唯一
     */
    private String name;
    
    /**
     * 变量类型
     */
    private VariableType type;
    
    /**
     * 是否必需
     */
    @Builder.Default
    private boolean required = true;
    
    /**
     * 变量描述
     */
    private String description;
    
    /**
     * 验证规则
     */
    private ValidationRule validation;
    
    /**
     * 默认值（当 required=false 时使用）
     */
    private Object defaultValue;
    
    /**
     * 验证变量定义的合法性
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Variable name cannot be empty");
        }
        
        if (type == null) {
            throw new IllegalArgumentException("Variable type cannot be null");
        }
        
        // 必需变量不应有默认值
        if (required && defaultValue != null) {
            throw new IllegalArgumentException(
                "Required variable '" + name + "' should not have a default value");
        }
        
        // 验证默认值类型
        if (defaultValue != null) {
            validateValueType(defaultValue);
        }
    }
    
    /**
     * 验证值的类型是否匹配
     */
    private void validateValueType(Object value) {
        switch (type) {
            case STRING:
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                        "Default value for variable '" + name + "' must be a String");
                }
                break;
            case NUMBER:
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(
                        "Default value for variable '" + name + "' must be a Number");
                }
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(
                        "Default value for variable '" + name + "' must be a Boolean");
                }
                break;
            // 其他类型的验证可以后续扩展
        }
    }
}
