package com.yupi.yuaiagent.memory.cognitive;

import com.yupi.yuaiagent.agent.specialized.AgentCapability;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.v2.event.MemoryConsolidationEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

@Slf4j
@AgentCapability(
        type = AgentType.MEMORY_CURATOR,
        description = "记忆管理Agent，负责记忆整理、情感标签、重要性评分、长期巩固",
        priority = 3
)
public class MemoryCuratorAgent implements SpecializedAgent {

    @Resource
    private CognitiveMemoryService cognitiveMemoryService;

    @Override
    public AgentType getAgentType() {
        return AgentType.MEMORY_CURATOR;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String chatId = context.getConversationId();
        String message = context.getMessage();
        Map<String, Object> emotionProfile = context.getEmotionProfile();

        // Store the current message with emotion tags
        cognitiveMemoryService.store(chatId, "user", message, emotionProfile);

        // Run consolidation
        cognitiveMemoryService.consolidate(chatId);

        long cost = System.currentTimeMillis() - start;
        log.info("[MemoryCuratorAgent-execute] chatId={}, costMs={}", chatId, cost);

        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("记忆整理完成")
                .data(Map.of("chatId", chatId, "consolidated", true))
                .build();
    }

    /**
     * Listen for memory consolidation events (async, fired after each conversation turn).
     */
    @EventListener
    public void onMemoryConsolidation(MemoryConsolidationEvent event) {
        String chatId = event.getConversationId();
        log.info("[MemoryCuratorAgent-onEvent] processing consolidation, chatId={}", chatId);
        cognitiveMemoryService.consolidate(chatId);
    }

    /**
     * Daily decay task — runs at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyDecay() {
        log.info("[MemoryCuratorAgent-dailyDecay] started");
        // In a real implementation, iterate all chatIds
        // For now, this is a placeholder
        log.info("[MemoryCuratorAgent-dailyDecay] completed");
    }
}
