package com.tencent.dataflow.app.dto;

import lombok.Data;
import java.util.Map;

@Data
public class NodeYamlDto {
    private String id;
    private String type; // taskType
    private Map<String, Object> config;
    private String startWhen;
    private Map<String, String> startPayload;
}
