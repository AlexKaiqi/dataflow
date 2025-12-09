package com.tencent.dataflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dataflow Application Entry Point
 *
 * @author dataflow-team
 */
@SpringBootApplication(scanBasePackages = {"com.tencent.dataflow", "com.alibaba.cola"})
public class DataflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataflowApplication.class, args);
    }
}
