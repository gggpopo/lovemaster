package com.yupi.yuaiagent.chatmemory.cloud;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云端记忆的本地缓存（用于“异步预取 + 下次对话使用”）。
 */
@Component
public class CloudMemoryCache {

    public record Snapshot(String context, Instant updatedAt) {
    }

    private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();

    public Optional<Snapshot> get(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(userId));
    }

    public void put(String userId, String context) {
        if (userId == null || userId.isBlank() || context == null || context.isBlank()) {
            return;
        }
        cache.put(userId, new Snapshot(context, Instant.now()));
    }
}

