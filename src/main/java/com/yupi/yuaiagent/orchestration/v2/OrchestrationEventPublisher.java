package com.yupi.yuaiagent.orchestration.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.orchestration.core.OrchestrationEvent;
import com.yupi.yuaiagent.orchestration.core.OrchestrationEventType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SSE v2 事件发布器。
 */
@Component
public class OrchestrationEventPublisher {

    private final ObjectMapper objectMapper;

    public OrchestrationEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publish(SseEmitter emitter,
                        String conversationId,
                        String traceId,
                        OrchestrationEventType type,
                        Map<String, Object> payload) throws IOException {
        OrchestrationEvent event = OrchestrationEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .conversationId(conversationId)
                .traceId(traceId)
                .type(type.name())
                .ts(System.currentTimeMillis())
                .payload(payload == null ? Map.of() : payload)
                .build();
        emitter.send(toJson(event));
    }

    public void publish(SseEmitter emitter,
                        String conversationId,
                        String traceId,
                        OrchestrationEventType type,
                        Object... kv) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                payload.put(String.valueOf(kv[i]), kv[i + 1]);
            }
        }
        publish(emitter, conversationId, traceId, type, payload);
    }

    private String toJson(OrchestrationEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }
}
