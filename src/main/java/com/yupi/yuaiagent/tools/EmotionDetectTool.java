package com.yupi.yuaiagent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 情绪检测工具：基于关键词匹配 + 权重打分，输出情绪类型与回复策略建议。
 */
@Slf4j
@Component
public class EmotionDetectTool {

    public enum Emotion {
        HAPPY,
        SAD,
        ANGRY,
        ANXIOUS,
        CONFUSED,
        NEUTRAL
    }

    private final ObjectMapper objectMapper;

    public EmotionDetectTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 关键词表（每个关键词出现一次 -> 对应情绪 +1 分）。
     * 说明：不依赖外部 API，仅做轻量启发式判断。
     */
    private static final Map<Emotion, List<String>> KEYWORDS = Map.of(
            // 注意：避免把“恋爱/爱情”等通用词误判为开心，因此不使用单字“爱”作为关键词
            Emotion.HAPPY, List.of("开心", "高兴", "哈哈", "太好了", "幸福", "甜蜜", "喜欢", "爱你", "我爱你"),
            Emotion.SAD, List.of("分手", "伤心", "哭", "难过", "失恋", "孤独", "想他", "想她"),
            Emotion.ANGRY, List.of("生气", "愤怒", "吵架", "冷战", "渣男", "渣女", "讨厌"),
            Emotion.ANXIOUS, List.of("紧张", "焦虑", "不安", "担心", "害怕", "怎么办", "纠结"),
            Emotion.CONFUSED, List.of("不懂", "为什么", "什么意思", "不理解", "搞不懂")
    );

    private static final Map<Emotion, String> SUGGESTIONS = Map.of(
            Emotion.HAPPY, "用户情绪积极，建议热情回应、正向强化，并顺势推进下一步沟通或约会计划。",
            Emotion.SAD, "用户情绪低落，建议温柔安慰，多倾听少说教，先共情再给可执行的小建议。",
            Emotion.ANGRY, "用户情绪激动，建议先安抚与降温，避免站队或刺激性表达，引导其描述事实与需求。",
            Emotion.ANXIOUS, "用户处于焦虑不安，建议给出明确结构（选项/步骤/时间线），降低不确定性并提供安全感。",
            Emotion.CONFUSED, "用户感到困惑，建议澄清问题、复述关键信息、提出几个具体追问，帮助其理清思路。",
            Emotion.NEUTRAL, "用户情绪中性，建议保持自然友好语气，先询问背景与目标，再提供针对性建议。"
    );

    @Tool(description = "分析用户消息中的情绪倾向，帮助调整回复的语气和策略")
    public String detectEmotion(
            @ToolParam(description = "用户的聊天消息文本") String message
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[EmotionDetectTool] detectEmotion start, messageLen={}", message == null ? 0 : message.length());

        try {
            String text = normalize(message);
            int totalKeywordCount = KEYWORDS.values().stream().mapToInt(List::size).sum();
            if (!StringUtils.hasText(text) || totalKeywordCount == 0) {
                return toJson(Emotion.NEUTRAL, 0.0);
            }

            Map<Emotion, Integer> scores = new LinkedHashMap<>();
            for (Emotion e : Emotion.values()) {
                scores.put(e, 0);
            }

            int matchedTotal = 0;
            Map<Emotion, Integer> matchedByEmotion = new LinkedHashMap<>();
            for (Map.Entry<Emotion, List<String>> entry : KEYWORDS.entrySet()) {
                Emotion emotion = entry.getKey();
                int matched = 0;
                for (String kw : entry.getValue()) {
                    if (StringUtils.hasText(kw) && text.contains(kw)) {
                        matched++;
                        scores.put(emotion, scores.get(emotion) + 1);
                    }
                }
                matchedByEmotion.put(emotion, matched);
                matchedTotal += matched;
            }

            Emotion best = Emotion.NEUTRAL;
            int bestScore = 0;
            for (Map.Entry<Emotion, Integer> e : scores.entrySet()) {
                if (e.getKey() == Emotion.NEUTRAL) {
                    continue;
                }
                if (e.getValue() > bestScore) {
                    bestScore = e.getValue();
                    best = e.getKey();
                }
            }

            double confidence = matchedTotal == 0 ? 0.0 : (matchedTotal * 1.0 / totalKeywordCount);

            log.info("[EmotionDetectTool] detectEmotion done, best={}, bestScore={}, matchedTotal={}, totalKeywords={}, costMs={}",
                    best, bestScore, matchedTotal, totalKeywordCount, System.currentTimeMillis() - startMs);
            log.debug("[EmotionDetectTool] scoreDetail={}, matchedDetail={}", scores, matchedByEmotion);

            return toJson(bestScore == 0 ? Emotion.NEUTRAL : best, confidence);
        } catch (Exception e) {
            log.error("[EmotionDetectTool] detectEmotion error", e);
            // 工具失败时返回可解析的兜底 JSON
            return toJson(Emotion.NEUTRAL, 0.0);
        }
    }

    private String toJson(Emotion emotion, double confidence) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("emotion", emotion.name());
            payload.put("confidence", round4(confidence));
            payload.put("suggestion", SUGGESTIONS.getOrDefault(emotion, SUGGESTIONS.get(Emotion.NEUTRAL)));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 最兜底：手动拼 JSON
            String suggestion = SUGGESTIONS.getOrDefault(emotion, SUGGESTIONS.get(Emotion.NEUTRAL));
            return "{\"emotion\":\"" + emotion.name() + "\",\"confidence\":" + round4(confidence) + ",\"suggestion\":\"" + escapeJson(suggestion) + "\"}";
        }
    }

    private String normalize(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
