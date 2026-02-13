package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地文件 + Redis 链路测试（不依赖外部 LLM / PgVector）。
 */
@Slf4j
class LocalRedisMemoryChainTest {

    private static final String REDIS_HOST = System.getProperty("redis.host", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getProperty("redis.port", "6379"));

    private LettuceConnectionFactory redisConnectionFactory;
    private StringRedisTemplate stringRedisTemplate;
    private Path localWindowDir;
    private final List<String> redisKeysToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        redisConnectionFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
        redisConnectionFactory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate(redisConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        Assumptions.assumeTrue(isRedisAvailable(),
                "Redis not reachable at %s:%s".formatted(REDIS_HOST, REDIS_PORT));

        localWindowDir = Files.createTempDirectory("memory-window-");
    }

    @AfterEach
    void tearDown() throws IOException {
        for (String key : redisKeysToCleanup) {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("cleanup redis key failed, key={}", key, e);
            }
        }
        redisKeysToCleanup.clear();

        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }

        if (localWindowDir != null) {
            deleteRecursively(localWindowDir);
        }
    }

    @Test
    void localWindowAndRedisSummary_shouldPersistAndVisualize() throws Exception {
        String conversationId = "local-redis-" + UUID.randomUUID();
        String summaryKey = "love_master:summary:" + conversationId;
        redisKeysToCleanup.add(summaryKey);

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new FileChatMemoryRepository(localWindowDir.toString()))
                .maxMessages(10)
                .build();

        List<Message> roundMessages = List.of(
                new UserMessage("U1"),
                new AssistantMessage("A1"),
                new UserMessage("U2"),
                new AssistantMessage("A2")
        );
        chatMemory.add(conversationId, roundMessages);

        ConversationSummaryService summaryService = new ConversationSummaryService(new NoopChatModel(), true, false);
        ReflectionTestUtils.setField(summaryService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(summaryService, "summaryTtlDays", 7L);
        summaryService.updateSummary(conversationId, List.of(new UserMessage("U1"), new AssistantMessage("A1")));

        List<Message> windowMessages = chatMemory.get(conversationId);
        String redisSummary = stringRedisTemplate.opsForValue().get(summaryKey);
        Long redisTtlSeconds = stringRedisTemplate.getExpire(summaryKey, TimeUnit.SECONDS);

        List<Path> localFiles;
        try (Stream<Path> stream = Files.list(localWindowDir)) {
            localFiles = stream
                    .filter(path -> path.getFileName().toString().endsWith(".kryo"))
                    .toList();
        }

        String visualization = MemoryStorageSnapshotFormatter.toMarkdown(
                conversationId,
                localWindowDir,
                localFiles,
                windowMessages,
                summaryKey,
                redisSummary,
                redisTtlSeconds
        );
        log.info("[LocalRedisMemoryChainTest]\n{}", visualization);

        assertFalse(localFiles.isEmpty(), "window messages should be persisted to local file");
        assertEquals(4, windowMessages.size(), "window should contain the written local messages");
        assertEquals("U1", windowMessages.get(0).getText());
        assertEquals("A1", windowMessages.get(1).getText());
        assertEquals("U2", windowMessages.get(2).getText());
        assertEquals("A2", windowMessages.get(3).getText());
        assertNotNull(redisSummary);
        assertTrue(redisSummary.contains("U1"), "redis summary should contain user message");
        assertTrue(redisSummary.contains("A1"), "redis summary should contain assistant message");
    }

    private boolean isRedisAvailable() {
        try {
            String pong = stringRedisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /**
     * 仅用于构造 ConversationSummaryService，当前测试关闭了 llm 摘要，不会调用到这里。
     */
    static class NoopChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("noop"))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }
    }

}
