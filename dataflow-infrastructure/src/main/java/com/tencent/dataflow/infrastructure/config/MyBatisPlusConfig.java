package com.tencent.dataflow.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatisPlusConfig - MyBatis-Plus 配置
 * 
 * @author dataflow
 */
@Configuration
@MapperScan("com.tencent.dataflow.infrastructure.persistence.**.mapper")
public class MyBatisPlusConfig {
    // MyBatis-Plus 的配置由 starter 自动完成
}
