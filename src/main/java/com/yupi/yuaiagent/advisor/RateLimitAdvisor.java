package com.yupi.yuaiagent.advisor;

import com.yupi.yuaiagent.tenant.RateLimitService;
import com.yupi.yuaiagent.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 限流 Advisor
 * <p>
 * 在 AI 请求前检查限流，超过限制时抛出异常
 */
@Slf4j
public class RateLimitAdvisor implements CallAdvisor, StreamAdvisor {

    private final RateLimitService rateLimitService;

    public RateLimitAdvisor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 优先级最高，在其他 Advisor 之前执行
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        checkRateLimit();
        return chain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        checkRateLimit();
        return chain.nextStream(chatClientRequest);
    }

    private void checkRateLimit() {
        TenantContext.TenantInfo tenant = TenantContext.getTenant();
        if (tenant == null) {
            // 没有租户信息，使用默认 key
            String key = "anonymous";
            if (!rateLimitService.allowRequest(key)) {
                throw new RateLimitExceededException("请求过于频繁，请稍后再试");
            }
            return;
        }

        String key = tenant.getRateLimitKey();
        if (!rateLimitService.allowRequest(key)) {
            int remaining = rateLimitService.getRemainingRequests(key);
            log.warn("Rate limit exceeded for key: {}, remaining: {}", key, remaining);
            throw new RateLimitExceededException("请求过于频繁，请稍后再试。剩余请求次数：" + remaining);
        }
    }

    /**
     * 限流异常
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
