package com.yupi.yuaiagent.orchestration.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * SSE v2 统一事件结构。
 */
@Data
@Builder
public class OrchestrationEvent {

    private String eventId;

    private String conversationId;

    private String traceId;

    private String type;

    private long ts;

    private Map<String, Object> payload;
}
