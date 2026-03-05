package com.yupi.yuaiagent.orchestration.v2.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Agent 间通信事件基类。
 */
@Getter
public abstract class AgentEvent extends ApplicationEvent {
    private final String conversationId;
    private final String traceId;

    protected AgentEvent(Object source, String conversationId, String traceId) {
        super(source);
        this.conversationId = conversationId;
        this.traceId = traceId;
    }
}
