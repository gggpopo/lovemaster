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
 * 约会策划 Agent —— 提供个性化约会方案和礼物建议。
 */
@Slf4j
@AgentCapability(
        type = AgentType.DATE_PLANNER,
        description = "约会策划Agent，提供约会方案设计和礼物推荐",
        supportedIntents = {"DATE_PLANNING", "GIFT_ADVICE"},
        priority = 8,
        supportsStreaming = true
)
public class DatePlannerAgent implements StreamableAgent {

    @Resource
    private LoveApp loveApp;

    private static final String BASE_SYSTEM_PROMPT;

    static {
        String loaded;
        try {
            loaded = new ClassPathResource("prompts/date_planner_system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            loaded = "你是一位创意十足的约会策划师和礼物顾问。";
        }
        BASE_SYSTEM_PROMPT = loaded;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.DATE_PLANNER;
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
            log.info("[DatePlannerAgent-execute] conversationId={}, responseLength={}, costMs={}",
                    conversationId, response == null ? 0 : response.length(), cost);

            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(false)
                    .summary(response)
                    .data(Map.of("response", response != null ? response : ""))
                    .build();
        } catch (Exception e) {
            log.error("[DatePlannerAgent-execute] conversationId={}", conversationId, e);
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
        log.info("[DatePlannerAgent-executeStream] conversationId={}, messageLength={}",
                context.getConversationId(),
                context.getMessage() == null ? 0 : context.getMessage().length());
        return loveApp.doChatByStream(context.getMessage(), context.getConversationId(), systemPrompt);
    }

    /**
     * 构建系统提示词，附加情绪上下文（如有）。
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
        Object valence = emotionProfile.get("overallValence");
        if (valence instanceof Number v) {
            sb.append("\n情绪倾向值：").append(String.format("%.2f", v.doubleValue()));
            if (v.doubleValue() < -0.3) {
                sb.append("\n用户情绪偏低，约会方案应侧重温馨治愈、减压放松的活动。");
            }
        }

        return sb.toString();
    }
}