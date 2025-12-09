package com.tencent.dataflow.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ValidationRule - 变量验证规则（值对象）
 * <p>
 * 定义对变量值的约束条件
 * </p>
 * 
 * @author dataflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRule {
    
    /**
     * 正则表达式（用于字符串验证）
     */
    private String pattern;
    
    /**
     * 最小长度（字符串或数组）
     */
    private Integer minLength;
    
    /**
     * 最大长度（字符串或数组）
     */
    private Integer maxLength;
    
    /**
     * 允许的值列表（枚举）
     */
    private List<Object> enumValues;
    
    /**
     * 最小值（数字类型）
     */
    private Double minValue;
    
    /**
     * 最大值（数字类型）
     */
    private Double maxValue;
    
    /**
     * 验证给定值是否符合规则
     */
    public boolean validate(Object value, VariableType type) {
        if (value == null) {
            return true; // null值由required字段控制
        }
        
        switch (type) {
            case STRING:
                return validateString((String) value);
            case NUMBER:
                return validateNumber(((Number) value).doubleValue());
            case ARRAY:
                return validateArray((List<?>) value);
            default:
                return true;
        }
    }
    
    /**
     * 验证字符串
     */
    private boolean validateString(String value) {
        // 检查长度
        if (minLength != null && value.length() < minLength) {
            return false;
        }
        if (maxLength != null && value.length() > maxLength) {
            return false;
        }
        
        // 检查正则表达式
        if (pattern != null && !value.matches(pattern)) {
            return false;
        }
        
        // 检查枚举值
        if (enumValues != null && !enumValues.contains(value)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证数字
     */
    private boolean validateNumber(double value) {
        if (minValue != null && value < minValue) {
            return false;
        }
        if (maxValue != null && value > maxValue) {
            return false;
        }
        
        // 检查枚举值
        if (enumValues != null && !enumValues.contains(value)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证数组
     */
    private boolean validateArray(List<?> value) {
        if (minLength != null && value.size() < minLength) {
            return false;
        }
        if (maxLength != null && value.size() > maxLength) {
            return false;
        }
        
        return true;
    }
}
