package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Set;

/**
 * 工具与资产执行 Agent。
 */
@AgentCapability(
    type = AgentType.ASSET,
    description = "执行工具调用链（约会地点推荐、天气查询、网页搜索等）",
    supportedIntents = {"DATE_PLANNING", "GIFT_ADVICE", "WEATHER_QUERY"},
    estimatedTokens = 200,
    inputSchema = "{\"message\": \"string\", \"conversationId\": \"string\", \"suggestedTools\": \"array\"}",
    outputSchema = "{\"ready\": \"boolean\"}"
)
@Component
public class AssetAgent implements SpecializedAgent {

    @Resource
    private LoveApp loveApp;

    @Override
    public AgentType getAgentType() {
        return AgentType.ASSET;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("工具调用待执行")
                .data(Map.of("ready", true))
                .build();
    }

    public String runToolChain(AgentContext context, String systemPrompt) {
        if (context == null) {
            return "";
        }
        Set<String> suggestedTools = context.getPolicy() == null ? Set.of() : context.getPolicy().getSuggestedTools();
        return loveApp.doChatWithTools(
                context.getMessage(),
                context.getConversationId(),
                suggestedTools,
                systemPrompt
        );
    }
}
