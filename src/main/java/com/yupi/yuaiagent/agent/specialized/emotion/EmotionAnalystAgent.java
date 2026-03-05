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
 * 深度情绪分析 Agent，基于 LLM 实现 12 种情绪识别、强度评分、趋势分析和危机检测。
 * 当 LLM 调用失败时自动降级为关键词分析。
 */
@Slf4j
@AgentCapability(
        type = AgentType.EMOTION_ANALYST,
        description = "深度情绪分析Agent，支持12种情绪、强度评分、趋势分析和危机检测",
        supportedIntents = {"EMOTION_SUPPORT", "CONFLICT_RESOLUTION", "COLD_WAR_REPAIR", "BREAKUP_RECOVERY"},
        priority = 10
)
public class EmotionAnalystAgent implements SpecializedAgent {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的情绪分析师。分析用户消息中的情绪状态，返回JSON格式结果。

            情绪类型（可多选，最多3个）：HAPPY, TOUCHED, EXPECTANT, CALM, CONFUSED, ANXIOUS, WRONGED, ANGRY, DISAPPOINTED, SAD, FEARFUL, DESPERATE

            返回格式（严格JSON，不要其他文字）：
            {
              "emotions": [{"type": "SAD", "intensity": 7, "weight": 0.6}, {"type": "ANXIOUS", "intensity": 4, "weight": 0.3}],
              "trend": "DECLINING",
              "overallValence": -0.6,
              "crisisDetected": false,
              "narrativeSummary": "用户表现出较强的失落感，伴随轻微焦虑"
            }

            规则：
            - intensity: 0-10（0=无感，5=中等，10=极端）
            - weight: 各情绪权重之和应为1.0
            - trend: IMPROVING/STABLE/DECLINING（如无历史信息则为STABLE）
            - overallValence: -1.0到1.0（负面到正面）
            - crisisDetected: 仅在检测到自伤、极端绝望等信号时为true
            """;

    @Override
    public AgentType getAgentType() {
        return AgentType.EMOTION_ANALYST;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String message = context == null || context.getMessage() == null ? "" : context.getMessage();

        if (message.isBlank()) {
            return buildNeutralOutput();
        }

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(message)
            ));
            String response = chatModel.call(prompt).getResult().getOutput().getText();
            Map<String, Object> parsed = parseEmotionResponse(response);

            // Calculate maxIntensity from the emotions list
            double maxIntensity = 0;
            Object emotions = parsed.get("emotions");
            if (emotions instanceof List<?> emotionList) {
                for (Object item : emotionList) {
                    if (item instanceof Map<?, ?> emotionMap) {
                        Object intensity = emotionMap.get("intensity");
                        if (intensity instanceof Number n && n.doubleValue() > maxIntensity) {
                            maxIntensity = n.doubleValue();
                        }
                    }
                }
            }
            parsed.put("maxIntensity", maxIntensity);

            long cost = System.currentTimeMillis() - start;
            log.info("[EmotionAnalystAgent-execute] conversationId={}, valence={}, crisisDetected={}, maxIntensity={}, costMs={}",
                    context.getConversationId(),
                    parsed.getOrDefault("overallValence", 0),
                    parsed.getOrDefault("crisisDetected", false),
                    maxIntensity, cost);

            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary((String) parsed.getOrDefault("narrativeSummary", "情绪分析完成"))
                    .data(parsed)
                    .build();

        } catch (Exception e) {
            log.warn("[EmotionAnalystAgent-execute] LLM analysis failed, falling back to keyword, conversationId={}",
                    context.getConversationId(), e);
            return fallbackAnalysis(message);
        }
    }

    private Map<String, Object> parseEmotionResponse(String response) throws Exception {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty LLM response");
        }
        // Extract JSON — LLM may wrap output in markdown code blocks
        String json = response.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private AgentOutput fallbackAnalysis(String message) {
        int positive = countKeywords(message, "开心", "高兴", "期待", "甜蜜", "舒服", "喜欢", "放松", "感动", "幸福");
        int negative = countKeywords(message, "焦虑", "失望", "生气", "难过", "担心", "崩溃", "冷淡", "委屈", "绝望", "害怕");

        String label = "CALM";
        double valence = 0.0;
        int intensity = 3;
        if (positive > negative) {
            label = "HAPPY";
            valence = Math.min(0.3 + (positive - negative) * 0.15, 0.9);
            intensity = Math.min(3 + (positive - negative), 8);
        } else if (negative > positive) {
            label = "SAD";
            valence = Math.max(-0.3 - (negative - positive) * 0.15, -0.9);
            intensity = Math.min(3 + (negative - positive), 8);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emotions", List.of(Map.of("type", label, "intensity", intensity, "weight", 1.0)));
        data.put("trend", "STABLE");
        data.put("overallValence", valence);
        data.put("crisisDetected", false);
        data.put("narrativeSummary", "情绪分析完成（关键词模式）");
        data.put("maxIntensity", (double) intensity);
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("情绪分析完成（关键词模式）")
                .data(data)
                .build();
    }

    private AgentOutput buildNeutralOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emotions", List.of(Map.of("type", "CALM", "intensity", 3, "weight", 1.0)));
        data.put("trend", "STABLE");
        data.put("overallValence", 0.0);
        data.put("crisisDetected", false);
        data.put("narrativeSummary", "无明显情绪信号");
        data.put("maxIntensity", 3.0);
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("无明显情绪信号")
                .data(data)
                .build();
    }

    private int countKeywords(String text, String... keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) {
                count++;
            }
        }
        return count;
    }
}
