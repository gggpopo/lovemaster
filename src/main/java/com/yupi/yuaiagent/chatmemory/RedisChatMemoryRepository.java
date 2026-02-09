package com.yupi.yuaiagent.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的对话记忆存储
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;

    public RedisChatMemoryRepository(RedisTemplate<String, Object> redisTemplate, long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        List<Message> existingMessages = get(conversationId);
        existingMessages.addAll(messages);

        try {
            List<Map<String, String>> serializedMessages = new ArrayList<>();
            for (Message msg : existingMessages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("type", msg.getMessageType().name());
                msgMap.put("content", msg.getText());
                serializedMessages.add(msgMap);
            }
            String json = objectMapper.writeValueAsString(serializedMessages);
            redisTemplate.opsForValue().set(key, json, ttlMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize messages for conversation: {}", conversationId, e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return new ArrayList<>();
        }

        try {
            String json = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            List<Map<String, String>> serializedMessages = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            List<Message> messages = new ArrayList<>();
            for (Map<String, String> msgMap : serializedMessages) {
                String type = msgMap.get("type");
                String content = msgMap.get("content");
                if (MessageType.USER.name().equals(type)) {
                    messages.add(new UserMessage(content));
                } else if (MessageType.ASSISTANT.name().equals(type)) {
                    messages.add(new AssistantMessage(content));
                }
            }
            return messages;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize messages for conversation: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
    }
}
