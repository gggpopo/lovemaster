package com.yupi.yuaiagent.orchestration.v2;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.router.IntentRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * V2 编排策略解析器。
 */
@Component
public class DefaultPolicyResolver {

    @Value("${app.orchestration.agent-message-threshold:120}")
    private int agentMessageThreshold = 120;

    public OrchestrationPolicy resolve(IntentRouter.RouteResult routeResult, ConversationMessageRequest request) {
        if (routeResult == null) {
            routeResult = new IntentRouter.RouteResult(IntentRouter.IntentType.CHITCHAT, 0.5, Set.of(), "standard", 0.5f);
        }
        String message = request == null ? "" : StrUtil.nullToDefault(request.getMessage(), "");
        Set<String> suggestedTools = routeResult.getSuggestedTools();
        ExecutionMode forceMode = parseForceMode(request == null ? null : request.getForceMode());
        if (forceMode != null) {
            return policy(forceMode, "force_mode", routeResult, suggestedTools);
        }
        if (request != null && request.getImages() != null && !request.getImages().isEmpty()) {
            return policy(ExecutionMode.VISION, "has_images", routeResult, suggestedTools);
        }
        if (routeResult.getIntentType() == IntentRouter.IntentType.UNSAFE) {
            return policy(ExecutionMode.BLOCK, "unsafe_intent", routeResult, suggestedTools);
        }
        switch (routeResult.getIntentType()) {
            case DATE_PLANNING, GIFT_ADVICE, IMAGE_REQUEST -> {
                return policy(ExecutionMode.TOOL, "intent_requires_tools", routeResult, suggestedTools);
            }
            case RELATIONSHIP_QA -> {
                if (isComplexTask(message)) {
                    return policy(ExecutionMode.AGENT, "complex_relationship_task", routeResult, suggestedTools);
                }
                return policy(ExecutionMode.CHAT, "relationship_qa_chat", routeResult, suggestedTools);
            }
            case LOVE_COPYWRITING, EMOTION_SUPPORT, CHITCHAT -> {
                return policy(ExecutionMode.CHAT, "direct_chat", routeResult, suggestedTools);
            }
            default -> {
                return policy(ExecutionMode.CHAT, "fallback_chat", routeResult, suggestedTools);
            }
        }
    }

    private boolean isComplexTask(String message) {
        if (StrUtil.isBlank(message)) {
            return false;
        }
        if (message.length() >= agentMessageThreshold) {
            return true;
        }
        String text = message.toLowerCase();
        return text.contains("步骤")
                || text.contains("计划")
                || text.contains("执行")
                || text.contains("清单")
                || text.contains("方案")
                || text.contains("拆解");
    }

    private ExecutionMode parseForceMode(String forceMode) {
        if (StrUtil.isBlank(forceMode)) {
            return null;
        }
        try {
            return ExecutionMode.valueOf(forceMode.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private OrchestrationPolicy policy(ExecutionMode mode,
                                       String reason,
                                       IntentRouter.RouteResult routeResult,
                                       Set<String> suggestedTools) {
        return OrchestrationPolicy.builder()
                .mode(mode)
                .reason(reason)
                .modelProfile(routeResult.getModelProfile())
                .temperature(routeResult.getTemperature())
                .suggestedTools(suggestedTools)
                .build();
    }
}
