package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 叙事生成 Agent。
 */
@Component
public class NarrativeAgent implements SpecializedAgent {

    @Resource
    private LoveApp loveApp;

    @Override
    public AgentType getAgentType() {
        return AgentType.NARRATIVE;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("开始生成回答")
                .data(Map.of("ready", true))
                .build();
    }

    public Flux<String> streamResponse(AgentContext context, String systemPrompt) {
        String conversationId = context == null ? "" : context.getConversationId();
        String message = context == null ? "" : context.getMessage();
        List<String> images = context == null || context.getImages() == null ? List.of() : context.getImages();
        ExecutionMode mode = context == null || context.getPolicy() == null ? ExecutionMode.CHAT : context.getPolicy().getMode();
        if (mode == ExecutionMode.VISION) {
            return loveApp.doChatWithVision(message, conversationId, images, systemPrompt);
        }
        return loveApp.doChatByStream(message, conversationId, systemPrompt);
    }
}
