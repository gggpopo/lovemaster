package com.yupi.yuaiagent.orchestration.v2.event;

import lombok.Getter;
import java.util.List;

/**
 * 记忆巩固事件（异步触发）。
 */
@Getter
public class MemoryConsolidationEvent extends AgentEvent {
    private final List<String> messageIds;

    public MemoryConsolidationEvent(Object source, String conversationId, String traceId,
                                    List<String> messageIds) {
        super(source, conversationId, traceId);
        this.messageIds = messageIds;
    }
}
