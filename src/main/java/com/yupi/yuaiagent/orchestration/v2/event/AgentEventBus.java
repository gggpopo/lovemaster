package com.yupi.yuaiagent.orchestration.v2.event;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Agent 间事件总线，基于 Spring ApplicationEvent。
 * 支持同步和异步事件发布。
 */
@Slf4j
@Component
public class AgentEventBus {

    @Resource
    private ApplicationEventPublisher publisher;

    /**
     * 同步发布事件。
     */
    public void publish(AgentEvent event) {
        log.info("[AgentEventBus-publish] conversationId={}, eventType={}, traceId={}",
                event.getConversationId(), event.getClass().getSimpleName(), event.getTraceId());
        publisher.publishEvent(event);
    }

    /**
     * 异步发布事件（需要 @EnableAsync 配置）。
     */
    @Async
    public void publishAsync(AgentEvent event) {
        log.info("[AgentEventBus-publishAsync] conversationId={}, eventType={}, traceId={}",
                event.getConversationId(), event.getClass().getSimpleName(), event.getTraceId());
        publisher.publishEvent(event);
    }
}
