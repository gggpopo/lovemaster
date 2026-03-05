package com.yupi.yuaiagent.orchestration.v3;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.specialized.AgentRegistry;
import com.yupi.yuaiagent.agent.specialized.NarrativeAgent;
import com.yupi.yuaiagent.agent.specialized.PlanAgent;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.conversation.model.ConversationEntity;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.core.CancellationToken;
import com.yupi.yuaiagent.orchestration.core.ConversationRuntime;
import com.yupi.yuaiagent.orchestration.core.ConversationRuntimeRegistry;
import com.yupi.yuaiagent.orchestration.core.OrchestrationEventType;
import com.yupi.yuaiagent.orchestration.v2.OrchestrationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * V3 动态编排服务（基于 PlanAgent）。
 * 核心特性：
 * 1. PlanAgent 分析意图，动态决定调用哪些 SubAgent
 * 2. 每个 SubAgent 拥有独立的上下文（inputContext），实现上下文隔离
 * 3. 减少不必要的 Agent 调用，降低 Token 消耗
 */
@Slf4j
@Service
public class DynamicOrchestrationService {

    private static final String INTERRUPTED_EXCEPTION_CODE = "__INTERRUPTED__";

    @Resource
    private ConversationStoreService conversationStoreService;

    @Resource
    private PlanAgent planAgent;

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private NarrativeAgent narrativeAgent;

    @Resource
    private OrchestrationEventPublisher eventPublisher;

    @Resource
    private ConversationRuntimeRegistry runtimeRegistry;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${app.orchestration.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    public SseEmitter streamConversation(String conversationId, ConversationMessageRequest request) {
        long start = System.currentTimeMillis();
        String normalizedConversationId = normalizeConversationId(conversationId);
        String traceId = StrUtil.blankToDefault(MDC.get("traceId"), "-");

        ConversationEntity conversationEntity = conversationStoreService.ensureConversation(
                normalizedConversationId,
                request == null ? null : request.getUserId(),
                request == null ? null : request.getPersonaId(),
                request == null ? null : request.getSceneId()
        );

        log.info("[DynamicOrchestrationService-streamConversation] {}",
                kv("conversationId", normalizedConversationId,
                        "traceId", traceId,
                        "messageLength", request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                        "personaId", conversationEntity == null ? "" : conversationEntity.getPersonaId()));

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        CancellationToken cancellationToken = new CancellationToken();

        ConversationRuntime runtime = ConversationRuntime.builder()
                .conversationId(normalizedConversationId)
                .cancellationToken(cancellationToken)
                .build();
        runtimeRegistry.start(normalizedConversationId, traceId);

        emitter.onCompletion(() -> {
            runtimeRegistry.finish(normalizedConversationId);
            log.info("[DynamicOrchestrationService-streamConversation] SSE completed: {}", normalizedConversationId);
        });

        emitter.onTimeout(() -> {
            cancellationToken.cancel("timeout");
            runtimeRegistry.finish(normalizedConversationId);
            log.warn("[DynamicOrchestrationService-streamConversation] SSE timeout: {}", normalizedConversationId);
        });

        emitter.onError(e -> {
            cancellationToken.cancel("error");
            runtimeRegistry.finish(normalizedConversationId);
            log.error("[DynamicOrchestrationService-streamConversation] SSE error: {}", normalizedConversationId, e);
        });

        CompletableFuture.runAsync(() -> {
            MDC.put("traceId", traceId);
            try {
                executeOrchestration(emitter, normalizedConversationId, conversationEntity, request, cancellationToken);
            } catch (Exception e) {
                log.error("[DynamicOrchestrationService-streamConversation] Orchestration failed", e);
                sendEvent(emitter, "error", Map.of("message", "编排执行失败: " + e.getMessage()));
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("[DynamicOrchestrationService-streamConversation] Failed to complete emitter", e);
                }
                MDC.clear();
            }
        });

        return emitter;
    }

    private void executeOrchestration(SseEmitter emitter,
                                      String conversationId,
                                      ConversationEntity conversationEntity,
                                      ConversationMessageRequest request,
                                      CancellationToken cancellationToken) {
        // Step 1: PlanAgent 生成执行计划
        AgentContext planContext = AgentContext.builder()
                .conversationId(conversationId)
                .userId(conversationEntity.getUserId())
                .personaId(conversationEntity.getPersonaId())
                .message(request.getMessage())
                .cancellationToken(cancellationToken)
                .build();

        AgentOutput planOutput = planAgent.execute(planContext);
        if (planOutput.isBlocked()) {
            sendEvent(emitter, "error", Map.of("message", "执行计划生成失败", "details", planOutput.getSummary()));
            return;
        }

        ExecutionPlan plan = (ExecutionPlan) planOutput.getData().get("plan");
        sendEvent(emitter, "plan_generated", Map.of(
                "intent", plan.getIntent(),
                "agentCount", plan.getRequiredAgents().size(),
                "estimatedTokens", plan.getEstimatedTokens(),
                "reasoning", plan.getReasoning()
        ));

        // Step 2: 根据计划动态调用 SubAgent（上下文隔离）
        Map<String, AgentOutput> agentOutputs = new LinkedHashMap<>();

        for (AgentTask task : plan.getRequiredAgents()) {
            if (cancellationToken.isCancelled()) {
                log.warn("[DynamicOrchestrationService-executeOrchestration] Cancelled by user");
                sendEvent(emitter, "cancelled", Map.of("message", "用户取消"));
                return;
            }

            sendEvent(emitter, "agent_started", Map.of(
                    "agentType", task.getAgentType().name(),
                    "priority", task.getPriority(),
                    "reason", task.getReason()
            ));

            // 为每个 SubAgent 构建独立的上下文（关键设计：上下文隔离）
            AgentContext isolatedContext = AgentContext.builder()
                    .conversationId(conversationId)
                    .userId(conversationEntity.getUserId())
                    .personaId(conversationEntity.getPersonaId())
                    .message(request.getMessage())
                    .sharedState(new HashMap<>(task.getInputContext()))  // 只包含该 Agent 需要的数据
                    .cancellationToken(cancellationToken)
                    .build();

            // 执行 SubAgent
            SpecializedAgent agent = agentRegistry.getAgent(task.getAgentType())
                    .orElseThrow(() -> new RuntimeException("Agent not found: " + task.getAgentType()));

            AgentOutput output = agent.execute(isolatedContext);
            agentOutputs.put(task.getAgentType().name(), output);

            sendEvent(emitter, "agent_finished", Map.of(
                    "agentType", task.getAgentType().name(),
                    "blocked", output.isBlocked(),
                    "summary", output.getSummary()
            ));

            // 如果 Agent 阻塞（如 SafetyAgent），立即返回
            if (output.isBlocked()) {
                sendEvent(emitter, "blocked", Map.of(
                        "agentType", task.getAgentType().name(),
                        "reason", output.getSummary()
                ));
                return;
            }
        }

        // 发送跳过的 Agent 信息
        if (plan.getSkipAgents() != null) {
            for (AgentType skippedAgent : plan.getSkipAgents()) {
                sendEvent(emitter, "agent_skipped", Map.of(
                        "agentType", skippedAgent.name(),
                        "reason", "not_required_for_" + plan.getIntent()
                ));
            }
        }

        // Step 3: 构建优化后的 System Prompt（只包含已执行的 Agent 输出）
        String systemPrompt = buildDynamicPrompt(agentOutputs);

        // Step 4: NarrativeAgent 生成回复
        AgentContext narrativeContext = AgentContext.builder()
                .conversationId(conversationId)
                .userId(conversationEntity.getUserId())
                .personaId(conversationEntity.getPersonaId())
                .message(request.getMessage())
                .sharedState(Map.of("systemPrompt", systemPrompt))
                .cancellationToken(cancellationToken)
                .build();

        Flux<String> responseFlux = narrativeAgent.streamResponse(narrativeContext, systemPrompt);

        responseFlux.subscribe(
            chunk -> {
                try {
                    emitter.send(SseEmitter.event().name("response_chunk").data(chunk));
                } catch (IOException e) {
                    log.error("[DynamicOrchestrationService-executeOrchestration] Failed to send chunk", e);
                }
            },
            error -> {
                log.error("[DynamicOrchestrationService-executeOrchestration] Stream error", error);
                sendEvent(emitter, "error", Map.of("message", "生成回复失败: " + error.getMessage()));
            },
            () -> {
                log.info("[DynamicOrchestrationService-executeOrchestration] Stream completed");
            }
        );
    }

    private String buildDynamicPrompt(Map<String, AgentOutput> agentOutputs) {
        StringBuilder sb = new StringBuilder("你是深耕恋爱心理领域的专家，回答温柔、尊重、可执行，并保持边界感。");

        for (Map.Entry<String, AgentOutput> entry : agentOutputs.entrySet()) {
            AgentOutput output = entry.getValue();
            if (output.getData() == null) {
                continue;
            }

            switch (AgentType.valueOf(entry.getKey())) {
                case PERSONA:
                    Object systemPrompt = output.getData().get("systemPrompt");
                    if (systemPrompt != null && StrUtil.isNotBlank(String.valueOf(systemPrompt))) {
                        sb.append("\n人格约束：").append(systemPrompt);
                    }
                    break;
                case EMOTION:
                    Object label = output.getData().get("label");
                    Object confidence = output.getData().get("confidence");
                    sb.append("\n用户当前情绪：").append(label).append("，置信度 ").append(confidence).append("。");
                    break;
                case MEMORY:
                    Object count = output.getData().get("count");
                    sb.append("\n可参考历史消息条数：").append(count).append("。");
                    break;
                case TOOLS:
                    Object toolResult = output.getData().get("toolResult");
                    if (toolResult != null) {
                        sb.append("\n工具调用结果：").append(toolResult);
                    }
                    break;
                default:
                    break;
            }
        }

        return sb.toString();
    }

    private void sendEvent(SseEmitter emitter, String eventType, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", eventType, "data", data));
            emitter.send(SseEmitter.event().name(eventType).data(json));
        } catch (IOException e) {
            log.error("[DynamicOrchestrationService-sendEvent] Failed to send event: {}", eventType, e);
        }
    }

    private String normalizeConversationId(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        return conversationId.trim();
    }
}
