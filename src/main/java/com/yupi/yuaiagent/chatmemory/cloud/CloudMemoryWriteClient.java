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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山云端记忆库（KnowledgeBase）写入客户端。
 * <p>
 * 说明：不同租户/产品线的“记忆库写入”接口字段可能略有差异，本实现提供一个
 * 相对通用的 upsert 结构（collection_name + memories[]），具体字段可按需调整。
 */
@Slf4j
@Component
public class CloudMemoryWriteClient {

    private final CloudMemoryProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudMemoryWriteClient(CloudMemoryProperties props) {
        this.props = props;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, props.getConnectTimeoutMs())))
                .build();
    }

    public JsonNode upsertProfileMemory(String userId, String content, Map<String, Object> metadata) {
        if (!props.isEnabled()) {
            return null;
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            log.debug("云端记忆已启用，但 token 为空，跳过写入");
            return null;
        }
        if (props.getUpsertUrl() == null || props.getUpsertUrl().isBlank()) {
            return null;
        }
        if (userId == null || userId.isBlank() || content == null || content.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(props.getUpsertUrl());
            String jsonBody = objectMapper.writeValueAsString(buildUpsertBody(content, metadata));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(200, props.getReadTimeoutMs())))
                    .header("Authorization", "Bearer " + props.getToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("云端记忆写入失败 status={} body={}", response.statusCode(), safeBody(response.body()));
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("云端记忆写入异常 userId={}", userId, e);
            return null;
        }
    }

    private Map<String, Object> buildUpsertBody(String content, Map<String, Object> metadata) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("collection_name", props.getCollectionName());
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("content", content);
        if (metadata != null && !metadata.isEmpty()) {
            memory.put("metadata", metadata);
        }
        root.put("memories", List.of(memory));
        return root;
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}

