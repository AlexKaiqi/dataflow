package com.tencent.dataflow.infrastructure.executor;

import com.tencent.dataflow.domain.executor.TaskExecutor;
import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.taskschema.AccessProtocol;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.StateDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HttpTaskExecutor - HTTP 任务执行器
 * <p>
 * 通用的 HTTP 协议执行器。
 * 根据 Node 配置中的 baseUrl 和 Action/State 定义中的 endpoint 拼接 URL。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpTaskExecutor implements TaskExecutor {

    private final RestTemplate restTemplate;

    @Override
    public String executeAction(Node node, ActionDefinition action, Map<String, Object> params) {
        if (action.getProtocol() != AccessProtocol.HTTP) {
            log.debug("Skipping non-HTTP action: {} for node {}", action.getName(), node.getId());
            return null; // 或者抛出异常，取决于是否支持多协议组合
        }

        String url = buildUrl(node, action.getEndpoint());
        HttpMethod method = getMethod(action.getProtocolConfig(), HttpMethod.POST);

        log.info("Executing HTTP Action: {} {} for Node {}", method, url, node.getId());

        // TODO: 支持从 Node Config 中获取 Auth Headers
        HttpHeaders headers = new HttpHeaders();
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

        try {
            // 默认期望返回 String，通常是 executionId 或简单的状态
            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to execute HTTP action [{}] for node [{}]", action.getName(), node.getId(), e);
            throw new RuntimeException("HTTP Action failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Object getState(Node node, StateDefinition state) {
        if (state.getProtocol() != AccessProtocol.HTTP) {
            return null;
        }

        String url = buildUrl(node, state.getEndpoint());
        HttpMethod method = getMethod(null, HttpMethod.GET);

        log.debug("Fetching HTTP State: {} {} for Node {}", method, url, node.getId());

        try {
            ResponseEntity<Object> response = restTemplate.exchange(url, method, null, Object.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get HTTP state [{}] for node [{}]", state.getName(), node.getId(), e);
            // 状态获取失败通常不应阻断流程，返回 null 或特定错误对象
            return null;
        }
    }

    private String buildUrl(Node node, String endpoint) {
        // 1. 如果 endpoint 是绝对路径，直接使用
        if (endpoint != null && (endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return endpoint;
        }

        // 2. 从 Node Config 获取 baseUrl
        Map<String, Object> config = node.getTaskConfig().getConfig();
        String baseUrl = (String) config.get("baseUrl");
        if (!StringUtils.hasText(baseUrl)) {
            // 尝试 host
            baseUrl = (String) config.get("host");
        }

        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Node config missing 'baseUrl' or 'host' for HTTP task. NodeId: " + node.getId());
        }

        // 3. 拼接
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        String path = endpoint != null ? endpoint : "";
        if (!path.startsWith("/") && StringUtils.hasText(path)) {
            path = "/" + path;
        }

        return baseUrl + path;
    }

    private HttpMethod getMethod(Map<String, Object> protocolConfig, HttpMethod defaultMethod) {
        if (protocolConfig != null && protocolConfig.containsKey("method")) {
            Object methodObj = protocolConfig.get("method");
            if (methodObj instanceof String) {
                return HttpMethod.valueOf(((String) methodObj).toUpperCase());
            }
        }
        return defaultMethod;
    }
}
