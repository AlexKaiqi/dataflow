package com.tencent.dataflow.app.service;

import com.tencent.dataflow.app.parser.PipelineYamlParser;
import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.pipeline.Pipeline;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.ControlPlaneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineAppService {

    private final PipelineYamlParser parser;
    private final NodeRepository nodeRepository;
    private final ControlPlaneService controlPlaneService;

    public Pipeline submitPipeline(String yamlContent) {
        Pipeline pipeline = parser.parse(yamlContent);
        log.info("Submitting pipeline: {}", pipeline.getId());
        
        // Save nodes
        if (pipeline.getNodes() != null) {
            for (Node node : pipeline.getNodes()) {
                nodeRepository.save(node);
            }
        }
        
        return pipeline;
    }

    public void triggerEvent(Event event) {
        log.info("External event triggered: {}", event);
        controlPlaneService.onEvent(event);
    }
    
    public void executeAction(String nodeId, String action, Map<String, Object> params) {
        Node node = nodeRepository.findById(nodeId);
        if (node != null) {
            controlPlaneService.executeAction(node, action, params);
        } else {
            log.warn("Node not found: {}", nodeId);
        }
    }
}
