package com.yupi.yuaiagent.orchestration.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConversationRuntimeRegistryTest {

    private final ConversationRuntimeRegistry registry = new ConversationRuntimeRegistry();

    @Test
    void interrupt_shouldMarkRuntimeCancelled() {
        String conversationId = "conv_1";
        ConversationRuntime runtime = registry.start(conversationId, "trace_1");

        boolean interrupted = registry.interrupt(conversationId, "user_cancel");

        Assertions.assertTrue(interrupted);
        Assertions.assertTrue(runtime.getCancellationToken().isCancelled());
        Assertions.assertEquals("user_cancel", runtime.getCancellationToken().getReason());
    }
}
