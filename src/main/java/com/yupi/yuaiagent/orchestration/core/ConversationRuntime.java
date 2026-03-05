package com.yupi.yuaiagent.orchestration.core;

import lombok.Builder;
import lombok.Data;

/**
 * 对话运行时上下文。
 */
@Data
@Builder
public class ConversationRuntime {

    private String conversationId;

    private String traceId;

    private long startedAt;

    private CancellationToken cancellationToken;
}
