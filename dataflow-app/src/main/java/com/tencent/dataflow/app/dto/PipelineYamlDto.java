package com.tencent.dataflow.app.dto;

import lombok.Data;
import java.util.List;

@Data
public class PipelineYamlDto {
    private String id;
    private String description;
    private List<NodeYamlDto> nodes;
}
