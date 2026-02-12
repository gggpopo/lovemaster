package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 对话摘要服务：将被滑动窗口淘汰的历史消息压缩为摘要（Redis 优先，内存兜底）。
 */
@Slf4j
@Service
public class ConversationSummaryService {

    private static final String SUMMARY_KEY_PREFIX = "love_master:summary:";

    private final ChatModel chatModel;

    /**
     * 仅当 app.memory.redis.enabled=true 时才建议启用 Redis 摘要存储。
     * （避免本地/测试环境无 Redis 时触发连接失败）
     */
    private final boolean redisEnabled;

    /**
     * 是否启用 LLM 生成摘要。
     *
     * - true：使用 ChatModel 生成更高质量摘要（需要有效 key）
     * - false：使用本地拼接/截断策略（便于调试、压测、离线环境）
     */
    private final boolean llmEnabled;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 不可用时的兜底存储。
     */
    private final Map<String, String> localSummaryCache = new ConcurrentHashMap<>();

    @Value("${app.memory.summary.ttl-days:7}")
    private long summaryTtlDays;

    public ConversationSummaryService(ChatModel chatModel,
                                      @Value("${app.memory.redis.enabled:false}") boolean redisEnabled,
                                      @Value("${app.memory.summary.llm-enabled:true}") boolean llmEnabled) {
        this.chatModel = chatModel;
        this.redisEnabled = redisEnabled;
        this.llmEnabled = llmEnabled;
    }

    public String getSummary(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return "";
        }
        String key = SUMMARY_KEY_PREFIX + conversationId;

        if (redisEnabled && stringRedisTemplate != null) {
            try {
                String v = stringRedisTemplate.opsForValue().get(key);
                return v == null ? "" : v;
            } catch (Exception e) {
                log.debug("读取 Redis 摘要失败，回退本地缓存 conversationId={}", conversationId, e);
            }
        }
        return localSummaryCache.getOrDefault(conversationId, "");
    }

    /**
     * 将淘汰的消息合并到已有摘要中。
     */
    public String updateSummary(String conversationId, List<Message> evictedMessages) {
        long startMs = System.currentTimeMillis();

        if (!StringUtils.hasText(conversationId) || evictedMessages == null || evictedMessages.isEmpty()) {
            return getSummary(conversationId);
        }

        String existingSummary = getSummary(conversationId);
        if (!StringUtils.hasText(existingSummary)) {
            existingSummary = "暂无历史摘要";
        }

        StringBuilder messagesText = new StringBuilder();
        for (Message msg : evictedMessages) {
            String role = (msg instanceof UserMessage) ? "用户" : "AI助手";
            String content = msg == null ? "" : msg.getText();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            messagesText.append(role).append(": ").append(content).append("\n");
        }

        if (!StringUtils.hasText(messagesText)) {
            return existingSummary;
        }

        try {
            String newSummary;

            // 调试/离线环境：不走 LLM，避免依赖外部服务
            if (!llmEnabled) {
                newSummary = (existingSummary + "\n" + messagesText).trim();
                // 简单截断，避免无限增长
                int maxLen = 500;
                if (newSummary.length() > maxLen) {
                    newSummary = newSummary.substring(newSummary.length() - maxLen);
                }
            } else {
                String summaryPrompt = """
                        你是一个对话摘要专家。请将以下对话历史压缩为简洁的摘要。
                        要求：
                        1. 保留所有关键事实信息（人名、日期、地点、偏好、重要事件）
                        2. 保留用户的情感状态和关系阶段描述
                        3. 删除寒暄、重复内容、无关闲聊
                        4. 摘要用第三人称描述，语言简洁
                        5. 控制在 500 字以内
                        6. 如果已有摘要和新对话中有冲突信息，以新对话为准

                        已有摘要：
                        %s

                        新增需要合并的对话：
                        %s

                        请输出更新后的完整摘要：
                        """.formatted(existingSummary, messagesText.toString());

                newSummary = chatModel.call(new Prompt(summaryPrompt)).getResult().getOutput().getText();
            }

            if (!StringUtils.hasText(newSummary)) {
                return existingSummary;
            }

            String key = SUMMARY_KEY_PREFIX + conversationId;
            boolean stored = false;
            if (redisEnabled && stringRedisTemplate != null) {
                try {
                    stringRedisTemplate.opsForValue().set(key, newSummary, summaryTtlDays, TimeUnit.DAYS);
                    stored = true;
                } catch (Exception e) {
                    log.warn("写入 Redis 摘要失败，回退本地缓存 conversationId={}", conversationId, e);
                }
            }
            if (!stored) {
                localSummaryCache.put(conversationId, newSummary);
            }

            log.info("对话摘要已更新, conversationId={}, len={}, costMs={}",
                    conversationId, newSummary.length(), System.currentTimeMillis() - startMs);
            return newSummary;
        } catch (Exception e) {
            log.error("摘要更新失败, conversationId={}", conversationId, e);
            return existingSummary;
        }
    }

    public void clearSummary(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        String key = SUMMARY_KEY_PREFIX + conversationId;
        localSummaryCache.remove(conversationId);
        if (redisEnabled && stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.debug("清除 Redis 摘要失败 conversationId={}", conversationId, e);
            }
        }
    }
}
