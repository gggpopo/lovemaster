package com.yupi.yuaiagent.agent.specialized.emotion;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.core.CancellationToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class EmotionAnalystAgentTest {

    private final EmotionAnalystAgent agent = new EmotionAnalystAgent();

    @Test
    void execute_emptyMessage_shouldReturnNeutral() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("")
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = agent.execute(context);

        Assertions.assertFalse(output.isBlocked());
        Assertions.assertEquals(AgentType.EMOTION_ANALYST, output.getAgentType());
        Assertions.assertEquals("CALM",
                ((List<Map<String, Object>>) output.getData().get("emotions")).get(0).get("type"));
        Assertions.assertEquals(0.0, output.getData().get("overallValence"));
        Assertions.assertFalse((Boolean) output.getData().get("crisisDetected"));
    }

    @Test
    void execute_nullMessage_shouldReturnNeutral() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message(null)
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = agent.execute(context);
        Assertions.assertEquals("无明显情绪信号", output.getSummary());
    }

    /**
     * Without ChatModel injected, execute() will fail LLM call and fall back to keyword analysis.
     */
    @Test
    void execute_positiveKeywords_shouldFallbackToKeywordAnalysis() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("今天很开心，也很期待见面，感觉很幸福")
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = agent.execute(context);

        // Falls back to keyword mode since no ChatModel
        Assertions.assertFalse(output.isBlocked());
        String summary = output.getSummary();
        Assertions.assertTrue(summary.contains("关键词模式") || summary.contains("情绪分析完成"));
        // Should detect positive
        List<Map<String, Object>> emotions = (List<Map<String, Object>>) output.getData().get("emotions");
        Assertions.assertEquals("HAPPY", emotions.get(0).get("type"));
    }

    @Test
    void execute_negativeKeywords_shouldDetectSadInFallback() {
        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .message("我很焦虑和失望，感觉很委屈")
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();

        AgentOutput output = agent.execute(context);

        List<Map<String, Object>> emotions = (List<Map<String, Object>>) output.getData().get("emotions");
        Assertions.assertEquals("SAD", emotions.get(0).get("type"));
        double valence = ((Number) output.getData().get("overallValence")).doubleValue();
        Assertions.assertTrue(valence < 0, "Negative keywords should produce negative valence");
    }

    @Test
    void getAgentType_shouldReturnEmotionAnalyst() {
        Assertions.assertEquals(AgentType.EMOTION_ANALYST, agent.getAgentType());
    }
}
