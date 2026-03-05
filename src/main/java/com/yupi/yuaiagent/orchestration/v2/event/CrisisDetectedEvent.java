package com.yupi.yuaiagent.orchestration.v2.event;

import lombok.Getter;
import java.util.Map;

/**
 * 危机信号检测事件。
 */
@Getter
public class CrisisDetectedEvent extends AgentEvent {
    private final Map<String, Object> emotionProfile;
    private final CrisisSeverity severity;

    public CrisisDetectedEvent(Object source, String conversationId, String traceId,
                               Map<String, Object> emotionProfile, CrisisSeverity severity) {
        super(source, conversationId, traceId);
        this.emotionProfile = emotionProfile;
        this.severity = severity;
    }
}
