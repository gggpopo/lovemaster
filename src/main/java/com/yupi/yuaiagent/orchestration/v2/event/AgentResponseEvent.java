package com.yupi.yuaiagent.orchestration.v2.event;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import lombok.Getter;

/**
 * Agent 回复生成事件。
 */
@Getter
public class AgentResponseEvent extends AgentEvent {
    private final AgentType agentType;
    private final String response;
    private final boolean needsReflection;

    public AgentResponseEvent(Object source, String conversationId, String traceId,
                              AgentType agentType, String response, boolean needsReflection) {
        super(source, conversationId, traceId);
        this.agentType = agentType;
        this.response = response;
        this.needsReflection = needsReflection;
    }
}
