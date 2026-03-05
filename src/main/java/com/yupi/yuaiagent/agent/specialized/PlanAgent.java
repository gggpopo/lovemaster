package com.yupi.yuaiagent.agent.specialized;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.v3.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 执行计划生成 Agent（V3 动态编排核心）。
 * 分析用户意图，决定调用哪些 SubAgent 及其执行顺序。
 */
@Slf4j
@AgentCapability(
    type = AgentType.PLAN,
    description = "分析用户意图，生成动态执行计划",
    supportedIntents = {"*"},
    estimatedTokens = 200,
    inputSchema = "{\"message\": \"string\", \"conversationId\": \"string\", \"personaId\": \"string\"}",
    outputSchema = "{\"intent\": \"string\", \"requiredAgents\": \"array\", \"skipAgents\": \"array\", \"estimatedTokens\": \"number\"}"
)
@Component
public class PlanAgent implements SpecializedAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/plan-agent-system.txt")
    private Resource systemPromptResource;

    @Value("${app.orchestration.v3.plan-agent.temperature:0.1}")
    private double temperature;

    @Value("${app.orchestration.v3.plan-agent.max-tokens:500}")
    private int maxTokens;

    private String systemPrompt;
    private ChatClient chatClient;

    public PlanAgent(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            this.chatClient = ChatClient.builder(chatModel).build();
            log.info("[PlanAgent-init] Initialized successfully");
        } catch (IOException e) {
            log.error("[PlanAgent-init] Failed to load system prompt", e);
            throw new RuntimeException("Failed to initialize PlanAgent", e);
        }
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.PLAN;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        if (context == null || StrUtil.isBlank(context.getMessage())) {
            log.warn("[PlanAgent-execute] Context or message is null");
            return buildErrorOutput("context_missing");
        }

        String message = context.getMessage();
        String conversationId = context.getConversationId();
        String personaId = context.getPersonaId();

        log.info("[PlanAgent-execute] {}", kv("message", message, "conversationId", conversationId, "personaId", personaId));

        try {
            ExecutionPlan plan = generatePlan(message, conversationId, personaId);
            log.info("[PlanAgent-execute] Plan generated: {}", kv("intent", plan.getIntent(), "agentCount", plan.getRequiredAgents().size(), "estimatedTokens", plan.getEstimatedTokens()));

            return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("执行计划生成完成")
                .data(Map.of(
                    "plan", plan,
                    "intent", plan.getIntent(),
                    "agentCount", plan.getRequiredAgents().size(),
                    "estimatedTokens", plan.getEstimatedTokens()
                ))
                .build();
        } catch (Exception e) {
            log.error("[PlanAgent-execute] Failed to generate plan", e);
            return buildErrorOutput("plan_generation_failed: " + e.getMessage());
        }
    }

    /**
     * 生成执行计划（核心方法）
     */
    public ExecutionPlan generatePlan(String message, String conversationId, String personaId) {
        BeanOutputConverter<ExecutionPlan> outputConverter = new BeanOutputConverter<>(ExecutionPlan.class);

        String userPrompt = buildUserPrompt(message, conversationId, personaId);

        String response = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();

        log.debug("[PlanAgent-generatePlan] LLM response: {}", response);

        // 解析 JSON 响应
        ExecutionPlan plan = outputConverter.convert(response);

        // 替换占位符
        if (plan != null && plan.getRequiredAgents() != null) {
            plan.getRequiredAgents().forEach(task -> {
                if (task.getInputContext() != null) {
                    Map<String, Object> inputContext = task.getInputContext();
                    inputContext.replaceAll((key, value) -> {
                        if (value instanceof String) {
                            String strValue = (String) value;
                            if ("{conversationId}".equals(strValue)) {
                                return conversationId;
                            }
                            if ("{personaId}".equals(strValue)) {
                                return personaId;
                            }
                        }
                        return value;
                    });
                }
            });
        }

        return plan;
    }

    private String buildUserPrompt(String message, String conversationId, String personaId) {
        return String.format(
            "用户消息：%s\n\n对话ID：%s\n人格ID：%s\n\n请生成执行计划（JSON格式）：",
            message,
            StrUtil.blankToDefault(conversationId, "unknown"),
            StrUtil.blankToDefault(personaId, "default")
        );
    }

    private AgentOutput buildErrorOutput(String errorMessage) {
        return AgentOutput.builder()
            .agentType(getAgentType())
            .blocked(true)
            .summary("执行计划生成失败")
            .data(Map.of("error", errorMessage))
            .build();
    }
}
