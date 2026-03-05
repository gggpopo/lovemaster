package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.conversation.model.PersonaEntity;
import com.yupi.yuaiagent.conversation.service.PersonaService;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 人格约束 Agent。
 */
@AgentCapability(
    type = AgentType.PERSONA,
    description = "加载人格配置，定义 AI 的回复风格和行为约束",
    supportedIntents = {"*"},
    estimatedTokens = 100,
    inputSchema = "{\"personaId\": \"string\"}",
    outputSchema = "{\"personaId\": \"string\", \"name\": \"string\", \"systemPrompt\": \"string\"}"
)
@Component
public class PersonaAgent implements SpecializedAgent {

    @Resource
    private PersonaService personaService;

    @Override
    public AgentType getAgentType() {
        return AgentType.PERSONA;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        String personaId = context == null ? null : context.getPersonaId();
        PersonaEntity personaEntity = personaService.getById(personaId);
        if (personaEntity == null) {
            personaEntity = personaService.getDefaultPersona();
        }
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("人格约束加载完成")
                .data(Map.of(
                        "personaId", personaEntity.getId(),
                        "name", personaEntity.getName(),
                        "systemPrompt", personaEntity.getSystemPrompt()
                ))
                .build();
    }
}
