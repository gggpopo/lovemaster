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
 * 情感陪伴 Agent —— 根据用户情绪动态调整回应策略。
 */
@Slf4j
@AgentCapability(
        type = AgentType.COMPANION,
        description = "情感陪伴Agent，提供日常聊天、情绪支持和主动关怀",
        supportedIntents = {"CHITCHAT", "EMOTION_SUPPORT", "LOVE_COPYWRITING", "PROACTIVE_CARE"},
        priority = 8,
        supportsStreaming = true
)
public class CompanionAgent implements StreamableAgent {

    @Resource
    private LoveApp loveApp;

    private static final String BASE_SYSTEM_PROMPT;

    static {
        String loaded;
        try {
            loaded = new ClassPathResource("prompts/companion_system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            loaded = "你是一位温暖、耐心、善解人意的情感陪伴师。";
        }
        BASE_SYSTEM_PROMPT = loaded;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.COMPANION;
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
            log.info("[CompanionAgent-execute] conversationId={}, responseLength={}, costMs={}",
                    conversationId, response == null ? 0 : response.length(), cost);

            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary(response)
                    .data(Map.of("response", response != null ? response : ""))
                    .build();
        } catch (Exception e) {
            log.error("[CompanionAgent-execute] conversationId={}", conversationId, e);
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
        log.info("[CompanionAgent-executeStream] conversationId={}, messageLength={}",
                context.getConversationId(),
                context.getMessage() == null ? 0 : context.getMessage().length());
        return loveApp.doChatByStream(context.getMessage(), context.getConversationId(), systemPrompt);
    }

    /**
     * 根据情绪画像动态构建系统提示词。
     * 策略矩阵：
     *   高负面 (valence < -0.5, intensity > 7) → 紧急安抚模式
     *   中负面 (valence < -0.3)                → 共情倾听模式
     *   中性                                    → 轻松闲聊模式
     *   正面 (valence > 0)                      → 分享喜悦模式
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

        if (valence < -0.5 && intensity > 7) {
            sb.append("\n\n【紧急安抚模式】用户情绪非常低落且强烈。")
              .append("请优先验证和接纳用户的感受，不要急于给建议。")
              .append("使用温暖、包容的语言，让用户感到被理解和支持。")
              .append("避免说'别难过'、'想开点'等否定情绪的话。");
        } else if (valence < -0.3) {
            sb.append("\n\n【共情倾听模式】用户情绪偏低。")
              .append("请先反映用户的感受，用温和的问题引导用户表达更多。")
              .append("适当给予情感支持，在用户准备好时再提供建议。");
        } else if (valence > 0) {
            sb.append("\n\n【分享喜悦模式】用户心情不错。")
              .append("真诚地为用户感到高兴，积极回应，鼓励用户分享更多开心的事。");
        } else {
            sb.append("\n\n【轻松闲聊模式】用户情绪平稳。")
              .append("保持温暖友好的语气，轻松自然地聊天。");
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