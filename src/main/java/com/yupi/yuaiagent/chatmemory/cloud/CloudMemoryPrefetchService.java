package com.yupi.yuaiagent.chatmemory.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 异步预取云端记忆，并写入本地缓存。
 */
@Slf4j
@Service
public class CloudMemoryPrefetchService {

    private static final List<String> CANDIDATE_TEXT_KEYS = List.of(
            "content", "text", "memory", "summary", "value", "description"
    );

    private final CloudMemoryProperties props;
    private final CloudMemorySearchClient client;
    private final CloudMemoryCache cache;

    public CloudMemoryPrefetchService(CloudMemoryProperties props,
                                     CloudMemorySearchClient client,
                                     CloudMemoryCache cache) {
        this.props = props;
        this.client = client;
        this.cache = cache;
    }

    @Async("cloudMemoryExecutor")
    public void prefetch(String userId, String query) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            JsonNode json = client.search(userId, query);
            if (json == null) {
                return;
            }

            String context = toContextText(json, props.getMaxContextChars());
            if (context != null && !context.isBlank()) {
                cache.put(userId, context);
            }
        } catch (Exception e) {
            log.debug("云端记忆预取失败 userId={} query={}", userId, query, e);
        }
    }

    /**
     * 把不确定结构的 JSON 尽量提炼为可读上下文。
     */
    static String toContextText(JsonNode root, int maxChars) {
        if (root == null) {
            return "";
        }

        List<String> snippets = new ArrayList<>();
        collectSnippets(root, 0, snippets, new HashSet<>());
        if (snippets.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("云端记忆（可能相关）：\n");
        int count = 0;
        for (String s : snippets) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String line = s.replaceAll("\\s+", " ").trim();
            if (line.length() > 200) {
                line = line.substring(0, 200) + "...";
            }
            sb.append("- ").append(line).append("\n");
            count++;
            if (count >= 10 || sb.length() >= maxChars) {
                break;
            }
        }

        String result = sb.toString();
        return result.length() <= maxChars ? result : result.substring(0, maxChars);
    }

    private static void collectSnippets(JsonNode node, int depth, List<String> out, Set<String> dedup) {
        if (node == null || depth > 6 || out.size() >= 30) {
            return;
        }
        if (node.isTextual()) {
            String v = node.asText();
            String k = v == null ? "" : v.trim();
            if (k.length() >= 6 && dedup.add(k)) {
                out.add(k);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectSnippets(child, depth + 1, out, dedup);
                if (out.size() >= 30) {
                    return;
                }
            }
            return;
        }
        if (node.isObject()) {
            // 优先抽取常见字段
            for (String key : CANDIDATE_TEXT_KEYS) {
                JsonNode v = node.get(key);
                if (v != null && v.isTextual()) {
                    String text = v.asText();
                    String k = text == null ? "" : text.trim();
                    if (k.length() >= 6 && dedup.add(k)) {
                        out.add(k);
                    }
                }
            }
            node.fields().forEachRemaining(e -> collectSnippets(e.getValue(), depth + 1, out, dedup));
        }
    }
}

