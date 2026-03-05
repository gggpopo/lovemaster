package com.yupi.yuaiagent.agent.specialized.emotion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.specialized.AgentCapability;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.*;

/**
 * 回复质量审查 Agent，从共情度、语气、实用性、安全性、文化敏感性五个维度评估回复质量，
 * 低于阈值时自动调用 LLM 生成优化版本。
 */
@Slf4j
@AgentCapability(
        type = AgentType.REFLECTION,
        description = "回复质量审查Agent，评估共情度、语气、实用性、安全性、文化敏感性",
        priority = 5
)
public class ReflectionAgent implements SpecializedAgent {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    private static final double PASS_THRESHOLD = 7.0;

    private static final String EVALUATE_PROMPT = """
            你是一个回复质量审查专家。评估以下AI回复的质量，并在需要时提供优化版本。

            用户情绪状态：%s
            对话场景：%s

            原始回复：
            %s

            请从以下5个维度评分（1-10分），并返回严格JSON格式：
            {
              "empathy": 8,
              "tone": 7,
              "practicality": 6,
              "safety": 9,
              "culturalSensitivity": 8,
              "overallScore": 7.6,
              "needsRefinement": true,
              "refinedResponse": "优化后的回复内容...",
              "reason": "语气偏冷，缺少共情表达"
            }

            评分标准：
            - empathy: 是否真正理解了用户的情感需求
            - tone: 语气是否匹配当前情绪状态和场景
            - practicality: 建议是否具体可执行
            - safety: 是否存在不当内容或可能造成伤害的建议
            - culturalSensitivity: 是否考虑了文化背景和社会规范
            - overallScore: 五项加权平均（empathy权重0.3, tone 0.25, practicality 0.2, safety 0.15, culturalSensitivity 0.1）
            - needsRefinement: overallScore < 7 时为true
            - refinedResponse: 仅在needsRefinement为true时提供优化版本，否则为空字符串
            """;

    @Override
    public AgentType getAgentType() {
        return AgentType.REFLECTION;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String conversationId = context == null ? "" : context.getConversationId();

        // Extract input from sharedState
        Map<String, Object> state = context != null && context.getSharedState() != null
                ? context.getSharedState() : Collections.emptyMap();
        String originalResponse = String.valueOf(state.getOrDefault("originalResponse", ""));
        String intent = String.valueOf(state.getOrDefault("intent", "CHITCHAT"));

        if (originalResponse.isBlank()) {
            return buildPassOutput(originalResponse, 10.0);
        }

        // Build emotion context string from emotionProfile
        String emotionContext = "未知";
        if (context != null && context.getEmotionProfile() != null) {
            Object summary = context.getEmotionProfile().get("narrativeSummary");
            if (summary != null) {
                emotionContext = String.valueOf(summary);
            }
        }

        try {
            String systemPrompt = String.format(EVALUATE_PROMPT, emotionContext, intent, originalResponse);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage("请评估上述回复的质量")
            ));
            String response = chatModel.call(prompt).getResult().getOutput().getText();
            Map<String, Object> evaluation = parseEvaluation(response);

            double overallScore = ((Number) evaluation.getOrDefault("overallScore", 7.0)).doubleValue();
            boolean needsRefinement = Boolean.TRUE.equals(evaluation.get("needsRefinement"))
                    || overallScore < PASS_THRESHOLD;
            String refinedResponse = needsRefinement
                    ? String.valueOf(evaluation.getOrDefault("refinedResponse", originalResponse))
                    : originalResponse;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("qualityScore", overallScore);
            data.put("refinedResponse", refinedResponse);
            data.put("wasRefined", needsRefinement && !refinedResponse.equals(originalResponse));
            data.put("dimensions", Map.of(
                    "empathy", evaluation.getOrDefault("empathy", 7),
                    "tone", evaluation.getOrDefault("tone", 7),
                    "practicality", evaluation.getOrDefault("practicality", 7),
                    "safety", evaluation.getOrDefault("safety", 9),
                    "culturalSensitivity", evaluation.getOrDefault("culturalSensitivity", 8)
            ));

            long cost = System.currentTimeMillis() - start;
            log.info("[ReflectionAgent-execute] conversationId={}, overallScore={}, wasRefined={}, intent={}, costMs={}",
                    conversationId, overallScore, data.get("wasRefined"), intent, cost);

            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary("质量评估完成，得分: " + String.format("%.1f", overallScore))
                    .data(data)
                    .build();

        } catch (Exception e) {
            log.warn("[ReflectionAgent-execute] evaluation failed, passing through, conversationId={}", conversationId, e);
            return buildPassOutput(originalResponse, 7.0);
        }
    }

    private Map<String, Object> parseEvaluation(String response) throws Exception {
        String json = response.trim();
        if (json.startsWith("```")) {
            int s = json.indexOf('{');
            int e = json.lastIndexOf('}');
            if (s >= 0 && e > s) {
                json = json.substring(s, e + 1);
            }
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private AgentOutput buildPassOutput(String originalResponse, double score) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("qualityScore", score);
        data.put("refinedResponse", originalResponse);
        data.put("wasRefined", false);
        data.put("dimensions", Map.of());
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("质量评估通过")
                .data(data)
                .build();
    }
}
