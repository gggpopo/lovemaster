package com.yupi.yuaiagent.chatmemory.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 火山云端记忆库（KnowledgeBase）检索客户端。
 * <p>
 * 该实现按用户提供的 curl 结构构造请求体。
 */
@Slf4j
@Component
public class CloudMemorySearchClient {

    private final CloudMemoryProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudMemorySearchClient(CloudMemoryProperties props) {
        this.props = props;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, props.getConnectTimeoutMs())))
                .build();
    }

    public JsonNode search(String userId, String query) {
        if (!props.isEnabled()) {
            return null;
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            log.debug("云端记忆已启用，但 token 为空，跳过检索");
            return null;
        }
        if (query == null || query.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(props.getSearchUrl());
            String jsonBody = objectMapper.writeValueAsString(buildRequestBody(userId, query));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(200, props.getReadTimeoutMs())))
                    .header("Authorization", "Bearer " + props.getToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("云端记忆检索失败 status={} body={}", response.statusCode(), safeBody(response.body()));
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("云端记忆检索异常 userId={} query={}", userId, shorten(query, 60), e);
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(String userId, String query) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("collection_name", props.getCollectionName());
        root.put("query", query);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("user_id", userId);
        filter.put("memory_type", props.getMemoryType());
        root.put("filter", filter);

        root.put("limit", props.getLimit());

        Map<String, Object> metadata = new HashMap<>();
        if (props.getDefaultUserId() != null) {
            metadata.put("default_user_id", props.getDefaultUserId());
        }
        if (props.getDefaultUserName() != null) {
            metadata.put("default_user_name", props.getDefaultUserName());
        }
        if (props.getDefaultAssistantId() != null) {
            metadata.put("default_assistant_id", props.getDefaultAssistantId());
        }
        if (props.getDefaultAssistantName() != null) {
            metadata.put("default_assistant_name", props.getDefaultAssistantName());
        }
        metadata.put("time", System.currentTimeMillis());
        root.put("metadata", metadata);

        return root;
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}

