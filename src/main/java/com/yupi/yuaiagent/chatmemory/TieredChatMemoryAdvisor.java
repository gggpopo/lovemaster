package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 三层记忆 Advisor：滑动窗口 + 摘要 + 向量检索（长期记忆）。
 */
@Slf4j
public class TieredChatMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String CTX_CONVERSATION_ID = "tiered.conversation_id";
    private static final String CTX_USER_TEXT = "tiered.user_text";

    private final ChatMemory chatMemory;
    private final ConversationSummaryService summaryService;
    private final VectorMemoryService vectorMemoryService;
    private final int vectorTopK;

    private TieredChatMemoryAdvisor(Builder builder) {
        this.chatMemory = builder.chatMemory;
        this.summaryService = builder.summaryService;
        this.vectorMemoryService = builder.vectorMemoryService;
        this.vectorTopK = builder.vectorTopK;
    }

    public static Builder builder(ChatMemory chatMemory) {
        return new Builder(chatMemory);
    }

    public static class Builder {
        private final ChatMemory chatMemory;
        private ConversationSummaryService summaryService;
        private VectorMemoryService vectorMemoryService;
        private int vectorTopK = 5;

        public Builder(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
        }

        public Builder summaryService(ConversationSummaryService summaryService) {
            this.summaryService = summaryService;
            return this;
        }

        public Builder vectorMemoryService(VectorMemoryService vectorMemoryService) {
            this.vectorMemoryService = vectorMemoryService;
            return this;
        }

        public Builder vectorTopK(int vectorTopK) {
            this.vectorTopK = vectorTopK;
            return this;
        }

        public TieredChatMemoryAdvisor build() {
            if (this.chatMemory == null) {
                throw new IllegalArgumentException("chatMemory 不能为空");
            }
            if (this.summaryService == null) {
                throw new IllegalArgumentException("summaryService 不能为空");
            }
            if (this.vectorMemoryService == null) {
                throw new IllegalArgumentException("vectorMemoryService 不能为空");
            }
            return new TieredChatMemoryAdvisor(this);
        }
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 尽量靠前，让后续日志/工具等看到增强后的 Prompt
        return -10;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        ChatClientRequest advised = before(chatClientRequest);
        ChatClientResponse resp = chain.nextCall(advised);
        observeAfter(resp);
        return resp;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        ChatClientRequest advised = before(chatClientRequest);
        Flux<ChatClientResponse> flux = chain.nextStream(advised);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(flux, this::observeAfter);
    }

    private ChatClientRequest before(ChatClientRequest req) {
        long startMs = System.currentTimeMillis();

        String conversationId = resolveConversationId(req);
        UserMessage currentUserMessage = resolveCurrentUserMessage(req.prompt());
        String userText = currentUserMessage == null ? "" : currentUserMessage.getText();

        // 1) 长期记忆（向量检索）
        String longTerm = "";
        if (StringUtils.hasText(userText)) {
            longTerm = vectorMemoryService.retrieveRelevantMemories(conversationId, userText, vectorTopK);
        }

        // 2) 摘要
        String summary = summaryService.getSummary(conversationId);

        // 3) 窗口消息
        List<Message> windowMessages = chatMemory.get(conversationId);
        List<Message> cleanedWindow = windowMessages == null ? List.of() : windowMessages.stream()
                .filter(m -> m != null && !(m instanceof SystemMessage))
                .toList();

        String baseSystem = extractSystemText(req.prompt());
        String enhancedSystem = buildEnhancedSystem(baseSystem, longTerm, summary);

        // 4) 组装新 Prompt：System + window + current user
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(new SystemMessage(enhancedSystem));
        newMessages.addAll(cleanedWindow);
        if (currentUserMessage != null) {
            newMessages.add(currentUserMessage);
        }

        Prompt newPrompt = new Prompt(newMessages, req.prompt().getOptions());

        // 5) 把 conversationId/userText 放到 context，供 observeAfter 使用
        // 注意：req.context() 可能是不可变 Map，避免原地修改
        Map<String, Object> ctx = new HashMap<>(req.context());
        ctx.put(CTX_CONVERSATION_ID, conversationId);
        ctx.put(CTX_USER_TEXT, userText);

        log.info("[TieredChatMemoryAdvisor] before, conversationId={}, windowSize={}, longMemLen={}, summaryLen={}, costMs={}",
                conversationId,
                cleanedWindow.size(),
                longTerm == null ? 0 : longTerm.length(),
                summary == null ? 0 : summary.length(),
                System.currentTimeMillis() - startMs);

        return new ChatClientRequest(newPrompt, ctx);
    }

    private void observeAfter(ChatClientResponse resp) {
        String conversationId = resolveConversationId(resp);
        String userText = String.valueOf(resp.context().getOrDefault(CTX_USER_TEXT, ""));

        try {
            // 1) 取出 AI 回复
            AssistantMessage assistantMessage = resp.chatResponse().getResult().getOutput();

            // 2) 加入滑动窗口前做快照，用于找出淘汰消息
            List<Message> before = chatMemory.get(conversationId);
            if (before == null) {
                before = List.of();
            }

            // 3) 写入 window（user + assistant）
            List<Message> toAdd = new ArrayList<>();
            if (StringUtils.hasText(userText)) {
                toAdd.add(new UserMessage(userText));
            }
            toAdd.add(assistantMessage);
            if (!toAdd.isEmpty()) {
                chatMemory.add(conversationId, toAdd);
            }

            // 4) 找淘汰消息 -> 异步更新摘要
            List<Message> after = chatMemory.get(conversationId);
            List<Message> evicted = findEvictedMessages(before, after);
            if (!evicted.isEmpty()) {
                CompletableFuture.runAsync(() -> summaryService.updateSummary(conversationId, evicted));
            }

            // 5) 异步写入向量记忆
            CompletableFuture.runAsync(() -> vectorMemoryService.saveMessages(conversationId, toAdd));
        } catch (Exception e) {
            log.debug("[TieredChatMemoryAdvisor] observeAfter failed, conversationId={}", conversationId, e);
        }
    }

    private List<Message> findEvictedMessages(List<Message> before, List<Message> after) {
        if (before == null || before.isEmpty()) {
            return List.of();
        }
        Set<String> afterSet = (after == null ? List.<Message>of() : after).stream()
                .filter(m -> m != null && !(m instanceof SystemMessage))
                .map(this::sig)
                .collect(Collectors.toCollection(HashSet::new));

        return before.stream()
                .filter(m -> m != null && !(m instanceof SystemMessage))
                .filter(m -> !afterSet.contains(sig(m)))
                .toList();
    }

    private String sig(Message m) {
        String type = m.getMessageType() == null ? "" : m.getMessageType().name();
        String content = m.getText();
        return type + ":" + (content == null ? "" : content);
    }

    private String resolveConversationId(ChatClientRequest req) {
        Object v = req.context().get(ChatMemory.CONVERSATION_ID);
        String id = v == null ? null : String.valueOf(v);
        if (StringUtils.hasText(id)) {
            return id;
        }
        return "default";
    }

    private String resolveConversationId(ChatClientResponse resp) {
        Object v = resp.context().get(CTX_CONVERSATION_ID);
        String id = v == null ? null : String.valueOf(v);
        if (StringUtils.hasText(id)) {
            return id;
        }
        Object v2 = resp.context().get(ChatMemory.CONVERSATION_ID);
        String id2 = v2 == null ? null : String.valueOf(v2);
        return StringUtils.hasText(id2) ? id2 : "default";
    }

    private UserMessage resolveCurrentUserMessage(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return null;
        }
        // 取最后一条 USER 消息作为“当前输入”
        for (int i = prompt.getInstructions().size() - 1; i >= 0; i--) {
            Message m = prompt.getInstructions().get(i);
            if (m instanceof UserMessage) {
                return (UserMessage) m;
            }
        }
        return null;
    }

    private String extractSystemText(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null) {
            return "";
        }
        for (Message m : prompt.getInstructions()) {
            if (m instanceof SystemMessage) {
                return m.getText();
            }
        }
        return "";
    }

    private String buildEnhancedSystem(String baseSystem, String longTermMemories, String summary) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(baseSystem)) {
            sb.append(baseSystem);
        }
        if (StringUtils.hasText(longTermMemories)) {
            sb.append("\n\n【长期记忆 - 与当前问题相关的历史信息】\n").append(longTermMemories);
        }
        if (StringUtils.hasText(summary)) {
            sb.append("\n\n【对话摘要 - 之前对话的关键信息】\n").append(summary);
        }
        sb.append("\n\n请基于以上记忆信息和当前对话，为用户提供个性化、连贯的回答。\n");
        return sb.toString();
    }
}
