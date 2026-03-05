package com.yupi.yuaiagent.orchestration.v2.intent;

import com.yupi.yuaiagent.router.IntentRouter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 增强意图分析器。
 * 在现有 IntentRouter 基础上增加情感场景细分。
 */
@Slf4j
@Component
public class IntentAnalyzer {

    @Resource
    private IntentRouter intentRouter;

    // 冲突相关关键词
    private static final Pattern CONFLICT_PATTERN = Pattern.compile(
            "吵架|争吵|矛盾|闹别扭|不理我|生气了|发脾气|摔门|冲突|不开心"
    );
    private static final Pattern COLD_WAR_PATTERN = Pattern.compile(
            "冷战|不说话|不回消息|已读不回|不理人|冷暴力|沉默|僵持"
    );
    private static final Pattern BREAKUP_PATTERN = Pattern.compile(
            "分手|分开|离开|不爱了|放弃|挽回|复合|前任|前男友|前女友|断联"
    );

    /**
     * 分析用户消息意图。
     */
    public IntentResult analyze(String userMessage) {
        long start = System.currentTimeMillis();

        // 1. 先用现有 IntentRouter 做基础分类
        IntentRouter.RouteResult routeResult = intentRouter.classify(userMessage);

        // 2. 在基础分类上做情感场景细分
        ExtendedIntentType extendedIntent = refineIntent(userMessage, routeResult.getIntentType());

        // 3. 判断是否高风险场景
        boolean highRisk = isHighRisk(extendedIntent);

        // 4. 判断是否需要情绪感知
        boolean emotionAware = needsEmotionAwareness(extendedIntent);

        // 5. 调整工具建议
        Set<String> tools = adjustTools(routeResult.getSuggestedTools(), extendedIntent);

        IntentResult result = IntentResult.builder()
                .primaryIntent(routeResult.getIntentType())
                .extendedIntent(extendedIntent)
                .confidence(routeResult.getConfidence())
                .suggestedTools(tools)
                .modelProfile(highRisk ? "careful" : routeResult.getModelProfile())
                .temperature(highRisk ? 0.3f : routeResult.getTemperature())
                .emotionAware(emotionAware)
                .highRisk(highRisk)
                .build();

        long cost = System.currentTimeMillis() - start;
        log.info("[IntentAnalyzer-analyze] primaryIntent={}, extendedIntent={}, confidence={}, highRisk={}, emotionAware={}, costMs={}",
                result.getPrimaryIntent(), result.getExtendedIntent(), result.getConfidence(),
                result.isHighRisk(), result.isEmotionAware(), cost);

        return result;
    }

    /**
     * 细化意图分类：在 EMOTION_SUPPORT / RELATIONSHIP_QA 基础上进一步区分冲突场景。
     */
    private ExtendedIntentType refineIntent(String message, IntentRouter.IntentType primaryIntent) {
        if (message == null || message.isBlank()) {
            return mapToExtended(primaryIntent);
        }

        // 冲突场景细分（优先级：分手 > 冷战 > 一般冲突）
        if (BREAKUP_PATTERN.matcher(message).find()) {
            return ExtendedIntentType.BREAKUP_RECOVERY;
        }
        if (COLD_WAR_PATTERN.matcher(message).find()) {
            return ExtendedIntentType.COLD_WAR_REPAIR;
        }
        if (CONFLICT_PATTERN.matcher(message).find()) {
            return ExtendedIntentType.CONFLICT_RESOLUTION;
        }

        return mapToExtended(primaryIntent);
    }

    /**
     * 将基础意图映射到扩展意图。
     */
    private ExtendedIntentType mapToExtended(IntentRouter.IntentType primaryIntent) {
        return switch (primaryIntent) {
            case CHITCHAT -> ExtendedIntentType.CHITCHAT;
            case EMOTION_SUPPORT -> ExtendedIntentType.EMOTION_SUPPORT;
            case DATE_PLANNING -> ExtendedIntentType.DATE_PLANNING;
            case GIFT_ADVICE -> ExtendedIntentType.GIFT_ADVICE;
            case LOVE_COPYWRITING -> ExtendedIntentType.LOVE_COPYWRITING;
            case RELATIONSHIP_QA -> ExtendedIntentType.RELATIONSHIP_QA;
            case IMAGE_REQUEST -> ExtendedIntentType.IMAGE_REQUEST;
            case UNSAFE -> ExtendedIntentType.UNSAFE;
        };
    }

    private boolean isHighRisk(ExtendedIntentType intent) {
        return intent == ExtendedIntentType.CONFLICT_RESOLUTION
                || intent == ExtendedIntentType.COLD_WAR_REPAIR
                || intent == ExtendedIntentType.BREAKUP_RECOVERY
                || intent == ExtendedIntentType.UNSAFE;
    }

    private boolean needsEmotionAwareness(ExtendedIntentType intent) {
        return intent != ExtendedIntentType.CHITCHAT
                && intent != ExtendedIntentType.IMAGE_REQUEST
                && intent != ExtendedIntentType.UNSAFE;
    }

    private Set<String> adjustTools(Set<String> baseTools, ExtendedIntentType intent) {
        // 冲突场景不需要工具，纯对话
        if (intent == ExtendedIntentType.CONFLICT_RESOLUTION
                || intent == ExtendedIntentType.COLD_WAR_REPAIR
                || intent == ExtendedIntentType.BREAKUP_RECOVERY) {
            return Set.of();
        }
        return baseTools;
    }
}
