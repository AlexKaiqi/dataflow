package com.tencent.dataflow.infrastructure.executor;

import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.node.TaskConfig;
import com.tencent.dataflow.domain.taskschema.AccessProtocol;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.StateDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpTaskExecutorTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private HttpTaskExecutor executor;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        executor = new HttpTaskExecutor(restTemplate);
    }

    @Test
    void testExecuteAction() {
        // Prepare Node
        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", "http://example.com");
        Node node = Node.builder()
                .id("node-1")
                .taskConfig(TaskConfig.builder().config(config).build())
                .build();

        // Prepare Action
        ActionDefinition action = ActionDefinition.builder()
                .name("start")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/api/start")
                .protocolConfig(Map.of("method", "POST"))
                .build();

        // Mock Server
        mockServer.expect(requestTo("http://example.com/api/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("job-123", MediaType.TEXT_PLAIN));

        // Execute
        String result = executor.executeAction(node, action, Map.of("param", "value"));

        // Verify
        assertEquals("job-123", result);
        mockServer.verify();
    }

    @Test
    void testGetState() {
        // Prepare Node
        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", "http://example.com");
        Node node = Node.builder()
                .id("node-1")
                .taskConfig(TaskConfig.builder().config(config).build())
                .build();

        // Prepare State
        StateDefinition state = StateDefinition.builder()
                .name("status")
                .protocol(AccessProtocol.HTTP)
                .endpoint("/api/status")
                .build();

        // Mock Server
        mockServer.expect(requestTo("http://example.com/api/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"RUNNING\"}", MediaType.APPLICATION_JSON));

        // Execute
        Object result = executor.getState(node, state);

        // Verify
        assertNotNull(result);
        mockServer.verify();
    }
}
