package com.tencent.dataflow.app.service;

import com.tencent.dataflow.app.parser.PipelineYamlParser;
import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.pipeline.Pipeline;
import com.tencent.dataflow.domain.repository.NodeRepository;
import com.tencent.dataflow.domain.service.impl.ControlPlaneServiceImpl;
import com.tencent.dataflow.domain.taskschema.AccessProtocol;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.TaskSchema;
import com.tencent.dataflow.infrastructure.executor.HttpTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PipelineYamlFlowTest {

    private PipelineAppService pipelineAppService;
    private HttpTaskExecutor httpTaskExecutor;
    private MockRestServiceServer mockServer;
    private Map<String, Node> nodeStore;

    @BeforeEach
    void setUp() {
        nodeStore = new java.util.LinkedHashMap<>();

        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        httpTaskExecutor = new HttpTaskExecutor(restTemplate);

        NodeRepository nodeRepo = new NodeRepository() {
            @Override
            public Node findById(String nodeId) {
                return nodeStore.get(nodeId);
            }

            @Override
            public List<Node> findAllActiveNodes() {
                return new ArrayList<>(nodeStore.values());
            }

            @Override
            public void save(Node node) {
                nodeStore.put(node.getId(), node);
            }
        };

        Map<String, TaskSchema> schemaRegistry = new HashMap<>();

        // Helper to create schema
        java.util.function.BiConsumer<String, String> registerSchema = (type, endpoint) -> {
            TaskSchema schema = new TaskSchema();
            schema.setType(type);
            schema.setActions(Map.of("start", ActionDefinition.builder()
                    .name("start")
                    .protocol(AccessProtocol.HTTP)
                    .endpoint(endpoint)
                    .protocolConfig(Map.of("method", "POST"))
                    .build()));
            schemaRegistry.put(type, schema);
        };

        registerSchema.accept("sql_task", "/start");
        registerSchema.accept("approval_task", "/start");
        registerSchema.accept("ray_task", "/start");
        registerSchema.accept("another_task", "/start");

        ControlPlaneServiceImpl controlPlaneService = new ControlPlaneServiceImpl(nodeRepo, httpTaskExecutor,
                schemaRegistry);
        PipelineYamlParser parser = new PipelineYamlParser();

        pipelineAppService = new PipelineAppService(parser, nodeRepo, controlPlaneService);
    }

    @Test
    void testYamlPipelineFlow() {
        String yaml = """
                id: complex-pipeline
                description: A complex pipeline with branching
                nodes:
                  - id: sql-node
                    type: sql_task
                    config:
                      baseUrl: http://sql-service
                      script: "SELECT * FROM table"
                  - id: approval-node
                    type: approval_task
                    config:
                      baseUrl: http://approval-service
                    startWhen: "sql_node.succeeded"
                  - id: ray-node
                    type: ray_task
                    config:
                      baseUrl: http://ray-service
                    startWhen: "approval_node.succeeded"
                  - id: another-node
                    type: another_task
                    config:
                      baseUrl: http://another-service
                    startWhen: "approval_node.failed"
                """;

        // 1. Submit Pipeline
        Pipeline pipeline = pipelineAppService.submitPipeline(yaml);
        assertNotNull(pipeline);
        assertEquals("complex-pipeline", pipeline.getId());
        assertEquals(4, nodeStore.size());

        // Mock expectations
        mockServer.expect(ExpectedCount.once(), requestTo("http://sql-service/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("sql-job-id", MediaType.TEXT_PLAIN));

        mockServer.expect(ExpectedCount.once(), requestTo("http://approval-service/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("approval-job-id", MediaType.TEXT_PLAIN));

        mockServer.expect(ExpectedCount.once(), requestTo("http://ray-service/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("ray-job-id", MediaType.TEXT_PLAIN));

        mockServer.expect(ExpectedCount.never(), requestTo("http://another-service/start"));

        // 2. Start SQL Node
        System.out.println(">>> Starting SQL Node...");
        pipelineAppService.executeAction("sql-node", "start", Map.of());

        // 3. Simulate SQL Success -> Triggers Approval
        System.out.println(">>> SQL Node Succeeded...");
        Event sqlEvent = Event.builder()
                .type("succeeded")
                .source("/pipelines/complex-pipeline/nodes/sql-node")
                .pipelineId("complex-pipeline")
                .build();
        pipelineAppService.triggerEvent(sqlEvent);

        // 4. Simulate Approval Success -> Triggers Ray & Another
        System.out.println(">>> Approval Node Succeeded...");
        Event approvalEvent = Event.builder()
                .type("succeeded")
                .source("/pipelines/complex-pipeline/nodes/approval-node")
                .pipelineId("complex-pipeline")
                .build();
        pipelineAppService.triggerEvent(approvalEvent);

        // 5. Verify all calls were made
        mockServer.verify();
    }
}
