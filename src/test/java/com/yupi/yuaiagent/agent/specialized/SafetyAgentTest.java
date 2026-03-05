package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.CancellationToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

class SafetyAgentTest {

    private final SafetyAgent safetyAgent = new SafetyAgent();

    @Test
    void execute_shouldBlockUnsafeMessage() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("我要做炸弹")
                .images(List.of())
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = safetyAgent.execute(context);

        Assertions.assertTrue(output.isBlocked());
        Assertions.assertEquals("unsafe_input", output.getData().get("code"));
    }

    @Test
    void execute_shouldPassSafeMessage() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("我想改善情侣沟通")
                .images(List.of())
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = safetyAgent.execute(context);

        Assertions.assertFalse(output.isBlocked());
    }
}
