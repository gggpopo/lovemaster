package com.yupi.yuaiagent.agent.specialized.domain;

import com.yupi.yuaiagent.agent.specialized.AgentCapability;
import com.yupi.yuaiagent.agent.specialized.StreamableAgent;
import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 冲突调解 Agent —— 中立调解关系冲突，提供沟通话术和修复方案。
 */
@Slf4j
@AgentCapability(
        type = AgentType.CONFLICT_MEDIATOR,
        description = "冲突调解Agent，处理关系冲突、冷战修复和分手挽回",
        supportedIntents = {"CONFLICT_RESOLUTION", "COLD_WAR_REPAIR", "BREAKUP_RECOVERY"},
        priority = 9,
        supportsStreaming = true
)
public class ConflictMediatorAgent implements StreamableAgent {

    @Resource
    private LoveApp loveApp;

    private static final String BASE_SYSTEM_PROMPT;

    static {
        String loaded;
        try {
            loaded = new ClassPathResource("prompts/conflict_mediator_system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            loaded = "你是一位专业的关系冲突调解师。";
        }
        BASE_SYSTEM_PROMPT = loaded;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.CONFLICT_MEDIATOR;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String conversationId = context.getConversationId();
        String message = context.getMessage();
        String systemPrompt = buildSystemPrompt(context);

        try {
            String response = loveApp.doChatByStream(message, conversationId, systemPrompt)
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block();
            long cost = System.currentTimeMillis() - start;
            log.info("[ConflictMediatorAgent-execute] conversationId={}, responseLength={}, costMs={}",
                    conversationId, response == null ? 0 : response.length(), cost);

            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary(response)
                    .data(Map.of("response", response != null ? response : ""))
                    .build();
        } catch (Exception e) {
            log.error("[ConflictMediatorAgent-execute] conversationId={}", conversationId, e);
            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary("处理时遇到了问题，请稍后再试")
                    .data(Map.of("response", "抱歉，我现在有点忙不过来，稍等一下好吗？", "error", true))
                    .build();
        }
    }

    @Override
    public Flux<String> executeStream(AgentContext context) {
        String systemPrompt = buildSystemPrompt(context);
        log.info("[ConflictMediatorAgent-executeStream] conversationId={}, messageLength={}",
                context.getConversationId(),
                context.getMessage() == null ? 0 : context.getMessage().length());
        return loveApp.doChatByStream(context.getMessage(), context.getConversationId(), systemPrompt);
    }

    /**
     * 根据情绪画像动态构建系统提示词。
     * 高愤怒 → 先冷静再调解；高悲伤 → 先验证情绪再分析。
     */
    private String buildSystemPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);

        Map<String, Object> emotionProfile = context.getEmotionProfile();
        if (emotionProfile == null) {
            return sb.toString();
        }

        Object narrativeSummary = emotionProfile.get("narrativeSummary");
        if (narrativeSummary != null) {
            sb.append("\n\n当前用户情绪状态：").append(narrativeSummary);
        }

        double valence = extractDouble(emotionProfile.get("overallValence"), 0.0);
        double intensity = extractDouble(emotionProfile.get("intensity"), 5.0);
        sb.append("\n情绪倾向值：").append(String.format("%.2f", valence));
        sb.append("，情绪强度：").append(String.format("%.1f", intensity));

        String dominantEmotion = emotionProfile.get("dominantEmotion") instanceof String s ? s : "";

        if ("anger".equalsIgnoreCase(dominantEmotion) || (valence < -0.5 && intensity > 7)) {
            sb.append("\n\n【冷静优先模式】用户当前情绪激动（愤怒/高强度负面）。")
              .append("请先帮助用户冷静下来，承认他们的愤怒是合理的。")
              .append("避免立即分析对错，等用户情绪缓和后再引导理性思考。")
              .append("可以使用'我理解你现在很生气'等共情表达。");
        } else if ("sadness".equalsIgnoreCase(dominantEmotion) || valence < -0.3) {
            sb.append("\n\n【情绪验证模式】用户当前情绪偏向悲伤/失落。")
              .append("请先充分验证用户的感受，让用户感到被理解。")
              .append("在用户准备好之后，再逐步引导分析冲突原因和解决方案。");
        } else {
            sb.append("\n\n用户情绪相对平稳，可以直接进入冲突分析和调解环节。");
        }

        return sb.toString();
    }

    private static double extractDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }
}