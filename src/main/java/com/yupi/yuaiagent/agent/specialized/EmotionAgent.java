package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 情绪识别 Agent（轻量规则版）。
 */
@AgentCapability(
    type = AgentType.EMOTION,
    description = "识别用户情绪状态（positive/negative/neutral），用于情感支持场景",
    supportedIntents = {"EMOTION_SUPPORT", "CHITCHAT", "CONFLICT_RESOLUTION"},
    estimatedTokens = 80,
    inputSchema = "{\"message\": \"string\"}",
    outputSchema = "{\"label\": \"string\", \"confidence\": \"number\"}"
)
@Component
public class EmotionAgent implements SpecializedAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.EMOTION;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        String text = context == null || context.getMessage() == null ? "" : context.getMessage();
        int positive = count(text, "开心", "高兴", "期待", "甜蜜", "舒服", "喜欢", "放松");
        int negative = count(text, "焦虑", "失望", "生气", "难过", "担心", "崩溃", "冷淡");

        String label = "neutral";
        double score = 0.5;
        if (positive > negative) {
            label = "positive";
            score = 0.5 + Math.min(positive - negative, 4) * 0.1;
        } else if (negative > positive) {
            label = "negative";
            score = 0.5 + Math.min(negative - positive, 4) * 0.1;
        }

        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("情绪分析完成")
                .data(Map.of(
                        "label", label,
                        "confidence", Math.min(score, 0.95)
                ))
                .build();
    }

    private int count(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int total = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                total++;
            }
        }
        return total;
    }
}
