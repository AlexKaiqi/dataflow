package com.tencent.dataflow.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试应用启动类
 */
@SpringBootApplication
@MapperScan("com.tencent.dataflow.infrastructure.persistence.*.mapper")
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
