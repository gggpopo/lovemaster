package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.CancellationToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

class EmotionAgentTest {

    private final EmotionAgent emotionAgent = new EmotionAgent();

    @Test
    void execute_shouldDetectPositiveEmotion() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("今天很开心，也很期待见面")
                .images(List.of())
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = emotionAgent.execute(context);

        Assertions.assertEquals("positive", output.getData().get("label"));
    }

    @Test
    void execute_shouldDetectNegativeEmotion() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("我现在很焦虑和失望，不知道怎么办")
                .images(List.of())
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = emotionAgent.execute(context);

        Assertions.assertEquals("negative", output.getData().get("label"));
    }
}
