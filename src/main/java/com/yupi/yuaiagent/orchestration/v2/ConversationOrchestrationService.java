package com.yupi.yuaiagent.orchestration.v2;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.specialized.AssetAgent;
import com.yupi.yuaiagent.agent.specialized.EmotionAgent;
import com.yupi.yuaiagent.agent.specialized.MemoryAgent;
import com.yupi.yuaiagent.agent.specialized.NarrativeAgent;
import com.yupi.yuaiagent.agent.specialized.PersonaAgent;
import com.yupi.yuaiagent.agent.specialized.SafetyAgent;
import com.yupi.yuaiagent.conversation.model.ConversationEntity;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.core.ConversationRuntime;
import com.yupi.yuaiagent.orchestration.core.ConversationRuntimeRegistry;
import com.yupi.yuaiagent.orchestration.core.OrchestrationEventType;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.router.IntentRouter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 多 Agent 编排主服务（SSE v2）。
 */
@Slf4j
@Service
public class ConversationOrchestrationService {

    private static final String INTERRUPTED_EXCEPTION_CODE = "__INTERRUPTED__";

    private final ConversationStoreService conversationStoreService;
    private final IntentRouter intentRouter;
    private final DefaultPolicyResolver policyResolver;
    private final SafetyAgent safetyAgent;
    private final EmotionAgent emotionAgent;
    private final MemoryAgent memoryAgent;
    private final PersonaAgent personaAgent;
    private final NarrativeAgent narrativeAgent;
    private final AssetAgent assetAgent;
    private final OrchestrationEventPublisher eventPublisher;
    private final ConversationRuntimeRegistry runtimeRegistry;

    @Value("${app.orchestration.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    public ConversationOrchestrationService(ConversationStoreService conversationStoreService,
                                            IntentRouter intentRouter,
                                            DefaultPolicyResolver policyResolver,
                                            SafetyAgent safetyAgent,
                                            EmotionAgent emotionAgent,
                                            MemoryAgent memoryAgent,
                                            PersonaAgent personaAgent,
                                            NarrativeAgent narrativeAgent,
                                            AssetAgent assetAgent,
                                            OrchestrationEventPublisher eventPublisher,
                                            ConversationRuntimeRegistry runtimeRegistry) {
        this.conversationStoreService = conversationStoreService;
        this.intentRouter = intentRouter;
        this.policyResolver = policyResolver;
        this.safetyAgent = safetyAgent;
        this.emotionAgent = emotionAgent;
        this.memoryAgent = memoryAgent;
        this.personaAgent = personaAgent;
        this.narrativeAgent = narrativeAgent;
        this.assetAgent = assetAgent;
        this.eventPublisher = eventPublisher;
        this.runtimeRegistry = runtimeRegistry;
    }

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

        log.info("[ConversationOrchestrationService-streamConversation] {}",
                kv("conversationId", normalizedConversationId,
                        "traceId", traceId,
                        "messageLength", request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                        "imageCount", request == null || request.getImages() == null ? 0 : request.getImages().size(),
                        "forceMode", request == null ? "" : request.getForceMode(),
                        "personaId", conversationEntity == null ? "" : conversationEntity.getPersonaId()));

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        ConversationRuntime runtime = runtimeRegistry.start(normalizedConversationId, traceId);

        CompletableFuture.runAsync(() -> execute(runtime, request, emitter, start));

        emitter.onTimeout(() -> {
            runtimeRegistry.interrupt(normalizedConversationId, "timeout");
            runtimeRegistry.finish(normalizedConversationId);
            try {
                eventPublisher.publish(emitter, normalizedConversationId, traceId, OrchestrationEventType.interrupted,
                        "reason", "timeout",
                        "durationMs", System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("[ConversationOrchestrationService-timeout] {}", kv("conversationId", normalizedConversationId), e);
            } finally {
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> runtimeRegistry.finish(normalizedConversationId));

        return emitter;
    }

    public boolean interrupt(String conversationId, String reason) {
        return runtimeRegistry.interrupt(conversationId, StrUtil.blankToDefault(reason, "user_interrupt"));
    }

    private void execute(ConversationRuntime runtime,
                         ConversationMessageRequest request,
                         SseEmitter emitter,
                         long start) {
        String conversationId = runtime.getConversationId();
        String traceId = runtime.getTraceId();
        String userMessage = request == null ? "" : StrUtil.nullToDefault(request.getMessage(), "").trim();
        List<String> images = request == null || request.getImages() == null ? List.of() : request.getImages();
        String assistantText = "";
        ExecutionMode mode = ExecutionMode.CHAT;

        try {
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.orchestration_started,
                    "protocol", "v2",
                    "compat", false,
                    "conversationId", conversationId);

            if (StrUtil.isBlank(userMessage) && images.isEmpty()) {
                failAndComplete(emitter, conversationId, traceId, start, "INVALID_ARGUMENT", "message 和 images 不能同时为空");
                return;
            }

            userMessage = StrUtil.blankToDefault(userMessage, "请基于当前上下文继续分析");
            conversationStoreService.saveMessage(conversationId, "user", userMessage, images, Map.of("traceId", traceId));

            IntentRouter.RouteResult routeResult = intentRouter.classify(userMessage);
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.intent_classified,
                    "intent", routeResult.getIntentType().name(),
                    "confidence", routeResult.getConfidence(),
                    "suggestedTools", routeResult.getSuggestedTools(),
                    "modelProfile", routeResult.getModelProfile(),
                    "temperature", routeResult.getTemperature());

            OrchestrationPolicy policy = policyResolver.resolve(routeResult, request);
            mode = policy.getMode();
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.policy_selected,
                    "mode", policy.getMode().name(),
                    "reason", policy.getReason(),
                    "suggestedTools", policy.getSuggestedTools());

            Map<String, Object> sharedState = new LinkedHashMap<>();
            AgentContext context = AgentContext.builder()
                    .conversationId(conversationId)
                    .userId(request == null ? "" : request.getUserId())
                    .personaId(request == null ? "default" : request.getPersonaId())
                    .sceneId(request == null ? "" : request.getSceneId())
                    .message(userMessage)
                    .images(images)
                    .routeResult(routeResult)
                    .policy(policy)
                    .sharedState(sharedState)
                    .cancellationToken(runtime.getCancellationToken())
                    .build();

            ensureNotInterrupted(runtime);
            AgentOutput safetyOutput = runAgent(emitter, traceId, context, safetyAgent);
            sharedState.put("safety", safetyOutput.getData());
            if (safetyOutput.isBlocked() || policy.getMode() == ExecutionMode.BLOCK) {
                assistantText = "我不能协助处理可能有伤害或违法风险的请求。你可以换一种安全、合法的方式描述需求，我会继续帮助你。";
                eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_chunk,
                        "content", assistantText);
                complete(emitter, conversationId, traceId, start, mode, assistantText, false);
                return;
            }

            ensureNotInterrupted(runtime);
            AgentOutput emotionOutput = runAgent(emitter, traceId, context, emotionAgent);
            sharedState.put("emotion", emotionOutput.getData());

            ensureNotInterrupted(runtime);
            AgentOutput memoryOutput = runAgent(emitter, traceId, context, memoryAgent);
            sharedState.put("memory", memoryOutput.getData());

            ensureNotInterrupted(runtime);
            AgentOutput personaOutput = runAgent(emitter, traceId, context, personaAgent);
            sharedState.put("persona", personaOutput.getData());

            if (policy.getMode() == ExecutionMode.TOOL) {
                ensureNotInterrupted(runtime);
                eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.tool_call_started,
                        "toolCount", policy.getSuggestedTools() == null ? 0 : policy.getSuggestedTools().size(),
                        "tools", policy.getSuggestedTools());
                String result = assetAgent.runToolChain(context, buildSystemPrompt(sharedState, policy));
                assistantText = StrUtil.nullToDefault(result, "");
                for (String chunk : splitText(assistantText, 120)) {
                    ensureNotInterrupted(runtime);
                    eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_chunk,
                            "content", chunk);
                }
                eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.tool_call_finished,
                        "resultLength", assistantText.length());
                complete(emitter, conversationId, traceId, start, mode, assistantText, false);
                return;
            }

            runAgent(emitter, traceId, context, narrativeAgent);
            String systemPrompt = buildSystemPrompt(sharedState, policy);
            StringBuilder textBuilder = new StringBuilder();
            Flux<String> flux = narrativeAgent.streamResponse(context, systemPrompt)
                    .filter(item -> item != null && !item.isBlank() && !"[DONE]".equals(item));

            flux.doOnNext(chunk -> {
                        ensureNotInterrupted(runtime);
                        try {
                            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_chunk,
                                    "content", chunk);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        textBuilder.append(chunk);
                    })
                    .blockLast();
            assistantText = textBuilder.toString();
            complete(emitter, conversationId, traceId, start, mode, assistantText, false);
        } catch (RuntimeException e) {
            if (INTERRUPTED_EXCEPTION_CODE.equals(e.getMessage())) {
                interrupted(emitter, conversationId, traceId, start);
                return;
            }
            log.error("[ConversationOrchestrationService-execute] {}",
                    kv("conversationId", conversationId, "traceId", traceId, "mode", mode), e);
            failAndComplete(emitter, conversationId, traceId, start, "INTERNAL_ERROR",
                    StrUtil.blankToDefault(e.getMessage(), "internal error"));
        } catch (Exception e) {
            log.error("[ConversationOrchestrationService-execute] {}",
                    kv("conversationId", conversationId, "traceId", traceId, "mode", mode), e);
            failAndComplete(emitter, conversationId, traceId, start, "INTERNAL_ERROR",
                    StrUtil.blankToDefault(e.getMessage(), "internal error"));
        } finally {
            if (assistantText != null && !assistantText.isBlank()) {
                conversationStoreService.saveMessage(conversationId, "assistant", assistantText, List.of(), Map.of("traceId", traceId));
            }
            runtimeRegistry.finish(conversationId);
        }
    }

    private AgentOutput runAgent(SseEmitter emitter,
                                 String traceId,
                                 AgentContext context,
                                 com.yupi.yuaiagent.agent.specialized.SpecializedAgent agent) throws IOException {
        String conversationId = context.getConversationId();
        AgentType agentType = agent.getAgentType();
        eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.agent_started,
                "agent", agentType.name());
        AgentOutput output = agent.execute(context);
        eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.agent_finished,
                "agent", agentType.name(),
                "blocked", output.isBlocked(),
                "summary", output.getSummary(),
                "data", output.getData());
        return output;
    }

    private void complete(SseEmitter emitter,
                          String conversationId,
                          String traceId,
                          long start,
                          ExecutionMode mode,
                          String content,
                          boolean error) throws IOException {
        conversationStoreService.updateConversationStatus(conversationId, error ? "FAILED" : "ACTIVE");
        eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_completed,
                "mode", mode.name(),
                "durationMs", System.currentTimeMillis() - start,
                "contentLength", content == null ? 0 : content.length(),
                "error", error);
        emitter.complete();
    }

    private void failAndComplete(SseEmitter emitter,
                                 String conversationId,
                                 String traceId,
                                 long start,
                                 String code,
                                 String message) {
        try {
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.failed,
                    "code", code,
                    "message", message);
            complete(emitter, conversationId, traceId, start, ExecutionMode.BLOCK, "", true);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void interrupted(SseEmitter emitter,
                             String conversationId,
                             String traceId,
                             long start) {
        try {
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.interrupted,
                    "reason", "user_interrupt",
                    "durationMs", System.currentTimeMillis() - start);
            conversationStoreService.updateConversationStatus(conversationId, "INTERRUPTED");
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void ensureNotInterrupted(ConversationRuntime runtime) {
        if (runtime != null
                && runtime.getCancellationToken() != null
                && runtime.getCancellationToken().isCancelled()) {
            throw new RuntimeException(INTERRUPTED_EXCEPTION_CODE);
        }
    }

    private List<String> splitText(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int step = Math.max(maxLen, 1);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(text.length(), i + step);
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }

    private String buildSystemPrompt(Map<String, Object> sharedState, OrchestrationPolicy policy) {
        StringBuilder sb = new StringBuilder("你是深耕恋爱心理领域的专家，回答温柔、尊重、可执行，并保持边界感。");
        if (policy != null && StrUtil.isNotBlank(policy.getModelProfile())) {
            sb.append("\n模型风格：").append(policy.getModelProfile()).append("。");
        }
        if (sharedState != null) {
            Object persona = sharedState.get("persona");
            if (persona instanceof Map<?, ?> personaMap) {
                Object prompt = personaMap.get("systemPrompt");
                if (prompt != null && StrUtil.isNotBlank(String.valueOf(prompt))) {
                    sb.append("\n人格约束：").append(prompt);
                }
            }
            Object emotion = sharedState.get("emotion");
            if (emotion instanceof Map<?, ?> emotionMap) {
                Object label = emotionMap.get("label");
                Object confidence = emotionMap.get("confidence");
                sb.append("\n用户当前情绪：").append(label).append("，置信度 ").append(confidence).append("。");
            }
            Object memory = sharedState.get("memory");
            if (memory instanceof Map<?, ?> memoryMap) {
                Object count = memoryMap.get("count");
                sb.append("\n可参考历史消息条数：").append(count).append("。");
            }
        }
        return sb.toString();
    }

    private String normalizeConversationId(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        return conversationId.trim();
    }
}
