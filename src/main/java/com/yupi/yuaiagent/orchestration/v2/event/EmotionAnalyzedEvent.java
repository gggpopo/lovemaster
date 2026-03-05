package com.yupi.yuaiagent.orchestration.v2.event;

import lombok.Getter;
import java.util.Map;

/**
 * 情绪分析完成事件。
 */
@Getter
public class EmotionAnalyzedEvent extends AgentEvent {
    private final Map<String, Object> emotionProfile;

    public EmotionAnalyzedEvent(Object source, String conversationId, String traceId,
                                Map<String, Object> emotionProfile) {
        super(source, conversationId, traceId);
        this.emotionProfile = emotionProfile;
    }
}
