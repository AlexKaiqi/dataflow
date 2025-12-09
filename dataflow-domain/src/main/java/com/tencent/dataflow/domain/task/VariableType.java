package com.tencent.dataflow.domain.task;

/**
 * VariableType - 变量类型枚举
 * 
 * @author dataflow
 */
public enum VariableType {
    
    /**
     * 字符串类型
     */
    STRING("字符串"),
    
    /**
     * 数字类型（整数或浮点数）
     */
    NUMBER("数字"),
    
    /**
     * 布尔类型
     */
    BOOLEAN("布尔"),
    
    /**
     * 数组类型
     */
    ARRAY("数组"),
    
    /**
     * 对象类型
     */
    OBJECT("对象"),
    
    /**
     * 文件类型
     */
    FILE("文件");
    
    private final String description;
    
    VariableType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
