package com.yupi.yuaiagent.orchestration.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理运行中的对话任务，支持中断。
 */
@Component
public class ConversationRuntimeRegistry {

    private final Map<String, ConversationRuntime> runtimeMap = new ConcurrentHashMap<>();

    public ConversationRuntime start(String conversationId, String traceId) {
        ConversationRuntime runtime = ConversationRuntime.builder()
                .conversationId(conversationId)
                .traceId(traceId)
                .startedAt(System.currentTimeMillis())
                .cancellationToken(new CancellationToken())
                .build();
        runtimeMap.put(conversationId, runtime);
        return runtime;
    }

    public ConversationRuntime get(String conversationId) {
        return runtimeMap.get(conversationId);
    }

    public boolean interrupt(String conversationId, String reason) {
        ConversationRuntime runtime = runtimeMap.get(conversationId);
        if (runtime == null || runtime.getCancellationToken() == null) {
            return false;
        }
        return runtime.getCancellationToken().cancel(reason);
    }

    public void finish(String conversationId) {
        runtimeMap.remove(conversationId);
    }
}
