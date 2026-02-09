package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三层缓存架构的对话记忆存储
 * <p>
 * 层级：本地内存 -> Redis -> 文件持久化
 * <p>
 * 读取时：优先从本地内存读取，未命中则从 Redis 读取，再未命中则从文件读取
 * 写入时：同时写入所有层级
 */
@Slf4j
public class TieredChatMemoryRepository implements ChatMemory {

    private final Map<String, List<Message>> localCache = new ConcurrentHashMap<>();
    private final ChatMemory redisMemory;
    private final ChatMemory fileMemory;

    /**
     * @param redisMemory Redis 记忆层，可为 null（禁用 Redis 时）
     * @param fileMemory  文件持久化层
     */
    public TieredChatMemoryRepository(ChatMemory redisMemory, ChatMemory fileMemory) {
        this.redisMemory = redisMemory;
        this.fileMemory = fileMemory;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 1. 更新本地缓存
        List<Message> localMessages = localCache.computeIfAbsent(conversationId, k -> new ArrayList<>());
        localMessages.addAll(messages);

        // 2. 写入 Redis（如果启用）
        if (redisMemory != null) {
            try {
                redisMemory.add(conversationId, messages);
            } catch (Exception e) {
                log.warn("Failed to write to Redis memory for conversation: {}", conversationId, e);
            }
        }

        // 3. 写入文件持久化
        if (fileMemory != null) {
            try {
                fileMemory.add(conversationId, messages);
            } catch (Exception e) {
                log.warn("Failed to write to file memory for conversation: {}", conversationId, e);
            }
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        // 1. 尝试从本地缓存读取
        List<Message> localMessages = localCache.get(conversationId);
        if (localMessages != null && !localMessages.isEmpty()) {
            return new ArrayList<>(localMessages);
        }

        // 2. 尝试从 Redis 读取
        if (redisMemory != null) {
            try {
                List<Message> redisMessages = redisMemory.get(conversationId);
                if (redisMessages != null && !redisMessages.isEmpty()) {
                    // 回填本地缓存
                    localCache.put(conversationId, new ArrayList<>(redisMessages));
                    return redisMessages;
                }
            } catch (Exception e) {
                log.warn("Failed to read from Redis memory for conversation: {}", conversationId, e);
            }
        }

        // 3. 尝试从文件读取
        if (fileMemory != null) {
            try {
                List<Message> fileMessages = fileMemory.get(conversationId);
                if (fileMessages != null && !fileMessages.isEmpty()) {
                    // 回填本地缓存和 Redis
                    localCache.put(conversationId, new ArrayList<>(fileMessages));
                    if (redisMemory != null) {
                        try {
                            // 清空后重新添加，避免重复
                            redisMemory.clear(conversationId);
                            redisMemory.add(conversationId, fileMessages);
                        } catch (Exception e) {
                            log.warn("Failed to backfill Redis memory for conversation: {}", conversationId, e);
                        }
                    }
                    return fileMessages;
                }
            } catch (Exception e) {
                log.warn("Failed to read from file memory for conversation: {}", conversationId, e);
            }
        }

        return new ArrayList<>();
    }

    @Override
    public void clear(String conversationId) {
        // 清除所有层级
        localCache.remove(conversationId);

        if (redisMemory != null) {
            try {
                redisMemory.clear(conversationId);
            } catch (Exception e) {
                log.warn("Failed to clear Redis memory for conversation: {}", conversationId, e);
            }
        }

        if (fileMemory != null) {
            try {
                fileMemory.clear(conversationId);
            } catch (Exception e) {
                log.warn("Failed to clear file memory for conversation: {}", conversationId, e);
            }
        }
    }
}
