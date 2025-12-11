package com.tencent.dataflow.domain.taskschema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ExecutorConfig - 执行器配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutorConfig {
    /**
     * 执行器基础 URL
     */
    private String baseUrl;
    
    /**
     * 认证 Token 或其他配置
     */
    private String authToken;
}
