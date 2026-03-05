package com.yupi.yuaiagent.orchestration.v2.event;

import lombok.Getter;

/**
 * 回复质量自检完成事件。
 */
@Getter
public class ReflectionCompletedEvent extends AgentEvent {
    private final String originalResponse;
    private final String refinedResponse;
    private final double qualityScore;
    private final boolean wasRefined;

    public ReflectionCompletedEvent(Object source, String conversationId, String traceId,
                                    String originalResponse, String refinedResponse,
                                    double qualityScore, boolean wasRefined) {
        super(source, conversationId, traceId);
        this.originalResponse = originalResponse;
        this.refinedResponse = refinedResponse;
        this.qualityScore = qualityScore;
        this.wasRefined = wasRefined;
    }
}
