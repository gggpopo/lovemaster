package com.yupi.yuaiagent.orchestration.v2;

import com.yupi.yuaiagent.agent.specialized.AgentRegistry;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.v2.intent.ExtendedIntentType;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Agent 选择器：根据意图 + 情绪画像选择最合适的主 Agent。
 *
 * 选择优先级：
 * 1. 危机检测 → SAFETY
 * 2. UNSAFE 意图 → SAFETY
 * 3. 高强度负面情绪 → COMPANION
 * 4. 意图驱动 → 对应专业 Agent
 * 5. 兜底 → NARRATIVE（现有的叙事 Agent）
 */
@Slf4j
@Component
public class AgentSelector {

    @Resource
    private AgentRegistry agentRegistry;

    /**
     * 选择主 Agent。
     *
     * @param intentResult   意图分析结果
     * @param emotionProfile 情绪画像（可为 null）
     * @return 选中的 Agent
     */
    public SpecializedAgent select(IntentResult intentResult, Map<String, Object> emotionProfile) {
        // 1. 危机优先
        if (isCrisis(emotionProfile)) {
            log.info("[AgentSelector-select] strategy=crisis, selectedAgent=SAFETY");
            return getAgentOrFallback(AgentType.SAFETY);
        }

        // 2. UNSAFE 意图 → SAFETY
        if (intentResult.getExtendedIntent() == ExtendedIntentType.UNSAFE) {
            log.info("[AgentSelector-select] strategy=unsafe_intent, selectedAgent=SAFETY");
            return getAgentOrFallback(AgentType.SAFETY);
        }

        // 3. 高强度负面情绪 → COMPANION（如果存在）
        if (isHighNegativeEmotion(emotionProfile)) {
            Optional<SpecializedAgent> companion = agentRegistry.getAgent(AgentType.COMPANION);
            if (companion.isPresent()) {
                log.info("[AgentSelector-select] strategy=emotion_priority, selectedAgent=COMPANION");
                return companion.get();
            }
        }

        // 4. 意图驱动选择
        AgentType targetType = mapIntentToAgent(intentResult.getExtendedIntent());
        Optional<SpecializedAgent> agent = agentRegistry.getAgent(targetType);
        if (agent.isPresent()) {
            log.info("[AgentSelector-select] strategy=intent_driven, intent={}, selectedAgent={}",
                    intentResult.getExtendedIntent(), targetType);
            return agent.get();
        }

        // 5. 兜底：NARRATIVE
        log.info("[AgentSelector-select] strategy=fallback, selectedAgent=NARRATIVE");
        return getAgentOrFallback(AgentType.NARRATIVE);
    }

    /**
     * 意图到 Agent 类型的映射。
     */
    private AgentType mapIntentToAgent(ExtendedIntentType intent) {
        return switch (intent) {
            case CHITCHAT, EMOTION_SUPPORT, LOVE_COPYWRITING, PROACTIVE_CARE -> AgentType.COMPANION;
            case DATE_PLANNING, GIFT_ADVICE -> AgentType.DATE_PLANNER;
            case CONFLICT_RESOLUTION, COLD_WAR_REPAIR, BREAKUP_RECOVERY -> AgentType.CONFLICT_MEDIATOR;
            case RELATIONSHIP_QA -> AgentType.NARRATIVE;
            case IMAGE_REQUEST -> AgentType.ASSET;
            case UNSAFE -> AgentType.SAFETY;
        };
    }

    private boolean isCrisis(Map<String, Object> emotionProfile) {
        if (emotionProfile == null) {
            return false;
        }
        Object crisis = emotionProfile.get("crisisDetected");
        return Boolean.TRUE.equals(crisis);
    }

    private boolean isHighNegativeEmotion(Map<String, Object> emotionProfile) {
        if (emotionProfile == null) {
            return false;
        }
        Object valence = emotionProfile.get("overallValence");
        Object maxIntensity = emotionProfile.get("maxIntensity");
        if (valence instanceof Number v && maxIntensity instanceof Number i) {
            return v.doubleValue() < -0.5 && i.doubleValue() > 7;
        }
        return false;
    }

    private SpecializedAgent getAgentOrFallback(AgentType type) {
        return agentRegistry.getAgent(type)
                .orElseGet(() -> agentRegistry.getAgent(AgentType.NARRATIVE)
                        .orElseThrow(() -> new IllegalStateException(
                                "No fallback agent (NARRATIVE) registered")));
    }
}
