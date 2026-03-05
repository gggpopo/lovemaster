package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.conversation.model.MessageEntity;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆召回 Agent。
 */
@AgentCapability(
    type = AgentType.MEMORY,
    description = "召回历史对话记录，提供上下文连续性",
    supportedIntents = {"EMOTION_SUPPORT", "DATE_PLANNING", "CONFLICT_RESOLUTION"},
    estimatedTokens = 150,
    inputSchema = "{\"conversationId\": \"string\", \"query\": \"string\"}",
    outputSchema = "{\"count\": \"number\", \"snippets\": \"array\"}"
)
@Component
public class MemoryAgent implements SpecializedAgent {

    @Resource
    private ConversationStoreService conversationStoreService;

    @Override
    public AgentType getAgentType() {
        return AgentType.MEMORY;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        String conversationId = context == null ? "" : context.getConversationId();
        List<MessageEntity> recent = conversationStoreService.listRecentMessages(conversationId, 6);
        List<Map<String, Object>> snippets = new ArrayList<>();
        for (MessageEntity item : recent) {
            Map<String, Object> row = new HashMap<>();
            row.put("role", item.getRole());
            row.put("content", item.getContent());
            row.put("createdAt", item.getCreatedAt());
            snippets.add(row);
        }
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("历史消息召回完成")
                .data(Map.of(
                        "count", snippets.size(),
                        "snippets", snippets
                ))
                .build();
    }
}
