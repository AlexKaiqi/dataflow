package com.tencent.dataflow.app.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tencent.dataflow.app.dto.NodeYamlDto;
import com.tencent.dataflow.app.dto.PipelineYamlDto;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.pipeline.Pipeline;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PipelineYamlParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public Pipeline parse(String yamlContent) {
        try {
            PipelineYamlDto dto = mapper.readValue(yamlContent, PipelineYamlDto.class);
            return convert(dto);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Pipeline YAML", e);
        }
    }

    private Pipeline convert(PipelineYamlDto dto) {
        List<Node> nodes = new ArrayList<>();
        if (dto.getNodes() != null) {
            nodes = dto.getNodes().stream().map(this::convertNode).collect(Collectors.toList());
        }
        
        return Pipeline.builder()
                .id(dto.getId())
                .nodes(nodes)
                .build();
    }

    private Node convertNode(NodeYamlDto nodeDto) {
        TaskConfig config = TaskConfig.builder()
                .taskType(nodeDto.getType())
                .config(nodeDto.getConfig())
                .build();

        return Node.builder()
                .id(nodeDto.getId())
                .taskConfig(config)
                .startWhen(nodeDto.getStartWhen())
                .startPayload(nodeDto.getStartPayload())
                .build();
    }
}
