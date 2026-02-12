package com.yupi.yuaiagent.chatmemory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三层记忆（滑动窗口 + 摘要 + 向量检索）Advisor 单测。
 * <p>
 * 目标：不依赖外部 LLM / Redis / PgVector，纯本地可跑。
 */
class TieredChatMemoryAdvisorTest {

    /**
     * 仅用于满足 ConversationSummaryService 的父类构造参数（本测试不依赖真实 LLM）。
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

    /**
     * 可控的摘要服务：记录 updateSummary 调用并可返回预设摘要。
     */
    static class TestSummaryService extends ConversationSummaryService {

        private final Map<String, String> summaryMap = new HashMap<>();
        private final CountDownLatch updateLatch;
        private volatile List<Message> lastEvicted;
        private final AtomicInteger updateCount = new AtomicInteger(0);

        TestSummaryService(CountDownLatch updateLatch) {
            super(new NoopChatModel(), false, false);
            this.updateLatch = updateLatch;
        }

        void putSummary(String conversationId, String summary) {
            summaryMap.put(conversationId, summary);
        }

        int getUpdateCount() {
            return updateCount.get();
        }

        List<Message> getLastEvicted() {
            return lastEvicted;
        }

        @Override
        public String getSummary(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                return "";
            }
            return summaryMap.getOrDefault(conversationId, "");
        }

        @Override
        public String updateSummary(String conversationId, List<Message> evictedMessages) {
            updateCount.incrementAndGet();
            lastEvicted = evictedMessages;
            if (updateLatch != null) {
                updateLatch.countDown();
            }
            String s = "UPDATED:" + (evictedMessages == null ? 0 : evictedMessages.size());
            summaryMap.put(conversationId, s);
            return s;
        }
    }

    /**
     * 可控的向量记忆服务：记录 saveMessages 调用并可返回预设召回文本。
     */
    static class TestVectorMemoryService extends VectorMemoryService {

        private final String memoryToReturn;
        private final CountDownLatch saveLatch;
        private final AtomicInteger saveCount = new AtomicInteger(0);
        private volatile List<Message> lastSaved;

        TestVectorMemoryService(String memoryToReturn, CountDownLatch saveLatch) {
            this.memoryToReturn = memoryToReturn;
            this.saveLatch = saveLatch;
        }

        int getSaveCount() {
            return saveCount.get();
        }

        List<Message> getLastSaved() {
            return lastSaved;
        }

        @Override
        public void saveMessages(String conversationId, List<Message> messages) {
            saveCount.incrementAndGet();
            lastSaved = messages;
            if (saveLatch != null) {
                saveLatch.countDown();
            }
        }

        @Override
        public String retrieveRelevantMemories(String conversationId, String query, int topK) {
            return memoryToReturn == null ? "" : memoryToReturn;
        }
    }

    private static ChatClientResponse buildResponse(String assistantText, Map<String, Object> context) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
        return new ChatClientResponse(chatResponse, context);
    }

    @Test
    void adviseCall_before_shouldInjectLongTermAndSummary_andAppendWindowMessages() {
        // 1) 准备：窗口记忆里已有历史消息
        String conversationId = "c1";
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
        chatMemory.add(conversationId, List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好呀")
        ));

        // 2) 使用可控 stub（避免 Mockito + 避免真实外部调用）
        TestSummaryService summaryService = new TestSummaryService(null);
        summaryService.putSummary(conversationId, "用户偏好：喜欢猫");
        TestVectorMemoryService vectorMemoryService = new TestVectorMemoryService("历史信息：用户提到过西湖", null);

        TieredChatMemoryAdvisor advisor = TieredChatMemoryAdvisor.builder(chatMemory)
                .summaryService(summaryService)
                .vectorMemoryService(vectorMemoryService)
                .vectorTopK(5)
                .build();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage("BASE_SYSTEM"),
                new UserMessage("今天去哪里约会？")
        ));
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(ChatMemory.CONVERSATION_ID, conversationId);
        ChatClientRequest req = new ChatClientRequest(prompt, ctx);

        // 3) stub chain：捕获最终传给下游模型的 request
        final List<ChatClientRequest> captured = new ArrayList<>(1);
        CallAdvisorChain chain = new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                captured.add(request);
                return buildResponse("OK", request.context());
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }
        };

        advisor.adviseCall(req, chain);

        assertEquals(1, captured.size());
        Prompt advisedPrompt = captured.get(0).prompt();
        List<Message> msgs = advisedPrompt.getInstructions();

        // System + window(2) + current user(1)
        assertEquals(4, msgs.size());
        assertTrue(msgs.get(0) instanceof SystemMessage);
        String systemText = msgs.get(0).getText();
        assertNotNull(systemText);
        assertTrue(systemText.contains("BASE_SYSTEM"));
        assertTrue(systemText.contains("【长期记忆"));
        assertTrue(systemText.contains("西湖"));
        assertTrue(systemText.contains("【对话摘要"));
        assertTrue(systemText.contains("喜欢猫"));

        assertTrue(msgs.get(1) instanceof UserMessage);
        assertEquals("你好", msgs.get(1).getText());
        assertTrue(msgs.get(2) instanceof AssistantMessage);
        assertEquals("你好呀", msgs.get(2).getText());
        assertTrue(msgs.get(3) instanceof UserMessage);
        assertEquals("今天去哪里约会？", msgs.get(3).getText());

        assertEquals(0, summaryService.getUpdateCount());
    }

    @Test
    void adviseCall_after_shouldEvictAndTriggerSummaryUpdate_andSaveVectorMemory() throws Exception {
        String conversationId = "c2";

        // maxMessages=2：每次对话写入 user+assistant 两条，第二次写入会把第一次淘汰掉
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(2)
                .build();

        CountDownLatch summaryLatch = new CountDownLatch(1);
        TestSummaryService summaryService = new TestSummaryService(summaryLatch);

        CountDownLatch vectorLatch = new CountDownLatch(2);
        TestVectorMemoryService vectorMemoryService = new TestVectorMemoryService("", vectorLatch);

        TieredChatMemoryAdvisor advisor = TieredChatMemoryAdvisor.builder(chatMemory)
                .summaryService(summaryService)
                .vectorMemoryService(vectorMemoryService)
                .vectorTopK(5)
                .build();

        AtomicInteger callSeq = new AtomicInteger(0);
        CallAdvisorChain chain = new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                // 模拟模型返回
                int n = callSeq.incrementAndGet();
                return buildResponse("A" + n, request.context());
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }
        };

        // 第一次
        {
            Prompt prompt = new Prompt(List.of(new SystemMessage("S"), new UserMessage("U1")));
            Map<String, Object> ctx = new HashMap<>();
            ctx.put(ChatMemory.CONVERSATION_ID, conversationId);
            advisor.adviseCall(new ChatClientRequest(prompt, ctx), chain);
        }
        // 第二次：触发淘汰 -> updateSummary
        {
            Prompt prompt = new Prompt(List.of(new SystemMessage("S"), new UserMessage("U2")));
            Map<String, Object> ctx = new HashMap<>();
            ctx.put(ChatMemory.CONVERSATION_ID, conversationId);
            advisor.adviseCall(new ChatClientRequest(prompt, ctx), chain);
        }

        // 等待异步任务执行
        assertTrue(vectorLatch.await(2, TimeUnit.SECONDS), "向量记忆写入未在预期时间内触发");
        assertTrue(summaryLatch.await(2, TimeUnit.SECONDS), "摘要更新未在预期时间内触发（可能未发生淘汰）");

        // 校验摘要更新触发（第二次写入会淘汰第一次的 2 条消息）
        assertEquals(1, summaryService.getUpdateCount());
        assertNotNull(summaryService.getLastEvicted());
        assertEquals(2, summaryService.getLastEvicted().size());

        // 校验向量写入至少触发两次（每轮一次）
        assertTrue(vectorMemoryService.getSaveCount() >= 2);
        assertNotNull(vectorMemoryService.getLastSaved());

        // 窗口里应该只保留最后一次的 user+assistant
        List<Message> window = chatMemory.get(conversationId);
        assertNotNull(window);
        assertEquals(2, window.size());
        assertTrue(window.get(0) instanceof UserMessage);
        assertEquals("U2", window.get(0).getText());
        assertTrue(window.get(1) instanceof AssistantMessage);
        assertEquals("A2", window.get(1).getText());
    }
}
