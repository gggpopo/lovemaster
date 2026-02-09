package com.yupi.yuaiagent.advisor;

import com.yupi.yuaiagent.chatmemory.cloud.CloudMemoryCache;
import com.yupi.yuaiagent.chatmemory.cloud.CloudMemoryPrefetchService;
import com.yupi.yuaiagent.chatmemory.cloud.CloudMemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * 云端记忆增强 Advisor。
 * <p>
 * - 每次请求：异步预取云端记忆（不阻塞当前对话）
 * - 若本地缓存里已有云端记忆：将其拼接到 user message 前作为上下文
 */
@Slf4j
@Component
public class CloudMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private final CloudMemoryProperties props;
    private final CloudMemoryCache cache;
    private final CloudMemoryPrefetchService prefetchService;

    public CloudMemoryAdvisor(CloudMemoryProperties props,
                             CloudMemoryCache cache,
                             CloudMemoryPrefetchService prefetchService) {
        this.props = props;
        this.cache = cache;
        this.prefetchService = prefetchService;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 放在日志之前/之后都可以，这里给一个较低优先级
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        return chain.nextCall(before(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        return chain.nextStream(before(chatClientRequest));
    }

    private ChatClientRequest before(ChatClientRequest req) {
        if (!props.isEnabled()) {
            return req;
        }

        String userText = req.prompt().getUserMessage() == null ? null : req.prompt().getUserMessage().getText();
        if (userText == null || userText.isBlank()) {
            return req;
        }

        String userId = resolveUserId(req);

        // 1) 异步预取：不影响本次响应
        try {
            prefetchService.prefetch(userId, userText);
        } catch (Exception e) {
            log.debug("触发云端记忆预取失败 userId={}", userId, e);
        }

        // 2) 使用上一轮缓存的云端记忆增强本次提示词
        Optional<CloudMemoryCache.Snapshot> snapshot = cache.get(userId);
        if (snapshot.isEmpty()) {
            return req;
        }

        String context = snapshot.get().context();
        if (context == null || context.isBlank()) {
            return req;
        }

        String newUserText = """
                【长期记忆】
                %s

                【当前问题】
                %s
                """.formatted(context, userText);

        Prompt newPrompt = req.prompt().augmentUserMessage(newUserText);
        return new ChatClientRequest(newPrompt, req.context());
    }

    private String resolveUserId(ChatClientRequest req) {
        Object v = req.context().get(ChatMemory.CONVERSATION_ID);
        String id = v == null ? null : String.valueOf(v);
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (props.getDefaultUserId() != null && !props.getDefaultUserId().isBlank()) {
            return props.getDefaultUserId();
        }
        // 保底，避免 null key
        return "anonymous";
    }
}

