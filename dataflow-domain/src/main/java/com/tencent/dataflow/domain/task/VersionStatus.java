package com.tencent.dataflow.domain.task;

/**
 * VersionStatus - 版本状态枚举
 * 
 * @author dataflow
 */
public enum VersionStatus {
    
    /**
     * 草稿状态 - 可修改，不可被流水线引用
     */
    DRAFT("草稿"),
    
    /**
     * 已发布状态 - 不可修改，可被流水线引用
     */
    PUBLISHED("已发布");
    
    private final String description;
    
    VersionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
