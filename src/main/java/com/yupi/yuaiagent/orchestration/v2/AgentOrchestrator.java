package com.yupi.yuaiagent.orchestration.v2;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.core.*;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * v3 多 Agent 编排器。
 * 通过 ExecutionCoordinator 管理 Agent 流水线，
 * 通过 OrchestrationEventPublisher 发布 SSE 事件。
 *
 * 启用方式：app.orchestration.version=v3
 */
@Slf4j
@Service
public class AgentOrchestrator {

    @Resource
    private ConversationStoreService conversationStoreService;

    @Resource
    private ExecutionCoordinator executionCoordinator;

    @Resource
    private OrchestrationEventPublisher eventPublisher;

    @Resource
    private ConversationRuntimeRegistry runtimeRegistry;

    @Resource(name = "agentExecutor")
    private Executor agentExecutor;

    @Value("${app.orchestration.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    private static final String INTERRUPTED_CODE = "__INTERRUPTED__";

    /**
     * 流式对话入口。
     */
    public SseEmitter streamConversation(String conversationId, ConversationMessageRequest request) {
        long start = System.currentTimeMillis();
        String normalizedId = normalizeConversationId(conversationId);
        String traceId = StrUtil.blankToDefault(MDC.get("traceId"), "-");

        conversationStoreService.ensureConversation(
                normalizedId,
                request == null ? null : request.getUserId(),
                request == null ? null : request.getPersonaId(),
                request == null ? null : request.getSceneId()
        );

        log.info("[AgentOrchestrator-streamConversation] conversationId={}, traceId={}, messageLength={}",
                normalizedId, traceId,
                request == null || request.getMessage() == null ? 0 : request.getMessage().length());

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        ConversationRuntime runtime = runtimeRegistry.start(normalizedId, traceId);

        CompletableFuture.runAsync(() -> executeWithSse(runtime, request, emitter, start), agentExecutor);

        emitter.onTimeout(() -> {
            runtimeRegistry.interrupt(normalizedId, "timeout");
            runtimeRegistry.finish(normalizedId);
            try {
                eventPublisher.publish(emitter, normalizedId, traceId, OrchestrationEventType.interrupted,
                        "reason", "timeout", "durationMs", System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("[AgentOrchestrator-timeout] conversationId={}", normalizedId, e);
            } finally {
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> runtimeRegistry.finish(normalizedId));

        return emitter;
    }

    public boolean interrupt(String conversationId, String reason) {
        return runtimeRegistry.interrupt(conversationId, StrUtil.blankToDefault(reason, "user_interrupt"));
    }

    private void executeWithSse(ConversationRuntime runtime,
                                ConversationMessageRequest request,
                                SseEmitter emitter,
                                long start) {
        String conversationId = runtime.getConversationId();
        String traceId = runtime.getTraceId();
        String userMessage = request == null ? "" : StrUtil.nullToDefault(request.getMessage(), "").trim();
        List<String> images = request == null || request.getImages() == null ? List.of() : request.getImages();
        String assistantText = "";

        try {
            // 1. 发布开始事件
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.orchestration_started,
                    "protocol", "v3", "conversationId", conversationId);

            // 2. 校验输入
            if (StrUtil.isBlank(userMessage) && images.isEmpty()) {
                failAndComplete(emitter, conversationId, traceId, start, "INVALID_ARGUMENT", "message 和 images 不能同时为空");
                return;
            }

            userMessage = StrUtil.blankToDefault(userMessage, "请基于当前上下文继续分析");
            conversationStoreService.saveMessage(conversationId, "user", userMessage, images, Map.of("traceId", traceId));

            // 3. 构建 AgentContext
            AgentContext context = AgentContext.builder()
                    .conversationId(conversationId)
                    .userId(request == null ? "" : request.getUserId())
                    .personaId(request == null ? "default" : request.getPersonaId())
                    .sceneId(request == null ? "" : request.getSceneId())
                    .message(userMessage)
                    .images(images)
                    .sharedState(new LinkedHashMap<>(Map.of("traceId", traceId)))
                    .cancellationToken(runtime.getCancellationToken())
                    .build();

            // 4. 执行 Agent 流水线
            ExecutionCoordinator.ExecutionResult result = executionCoordinator.execute(context);

            // 5. 发布意图分析事件
            IntentResult intent = result.intentResult();
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.intent_classified,
                    "intent", intent.getExtendedIntent().name(),
                    "confidence", intent.getConfidence(),
                    "highRisk", intent.isHighRisk());

            // 6. 发布 Agent 选择事件
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.agent_selected,
                    "agent", result.selectedAgent().getAgentType().name());

            // 7. 发布情绪分析事件
            if (result.emotionProfile() != null) {
                eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.emotion_analyzed,
                        "emotionProfile", result.emotionProfile());
            }

            // 8. 发布回复（分块）
            assistantText = result.finalResponse();
            if (assistantText != null && !assistantText.isBlank()) {
                for (String chunk : splitText(assistantText, 120)) {
                    ensureNotInterrupted(runtime);
                    eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_chunk,
                            "content", chunk);
                }
            }

            // 9. 发布反思结果（如果有）
            if (result.reflected()) {
                eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.reflection_completed,
                        "qualityScore", result.qualityScore(),
                        "wasRefined", true);
            }

            // 10. 完成
            complete(emitter, conversationId, traceId, start, assistantText, false);

        } catch (RuntimeException e) {
            if (INTERRUPTED_CODE.equals(e.getMessage())) {
                interrupted(emitter, conversationId, traceId, start);
                return;
            }
            log.error("[AgentOrchestrator-executeWithSse] conversationId={}, traceId={}", conversationId, traceId, e);
            failAndComplete(emitter, conversationId, traceId, start, "INTERNAL_ERROR",
                    StrUtil.blankToDefault(e.getMessage(), "internal error"));
        } catch (Exception e) {
            log.error("[AgentOrchestrator-executeWithSse] conversationId={}, traceId={}", conversationId, traceId, e);
            failAndComplete(emitter, conversationId, traceId, start, "INTERNAL_ERROR",
                    StrUtil.blankToDefault(e.getMessage(), "internal error"));
        } finally {
            if (assistantText != null && !assistantText.isBlank()) {
                conversationStoreService.saveMessage(conversationId, "assistant", assistantText, List.of(), Map.of("traceId", traceId));
            }
            runtimeRegistry.finish(conversationId);
        }
    }

    private void complete(SseEmitter emitter, String conversationId, String traceId,
                          long start, String content, boolean error) throws IOException {
        conversationStoreService.updateConversationStatus(conversationId, error ? "FAILED" : "ACTIVE");
        eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.response_completed,
                "durationMs", System.currentTimeMillis() - start,
                "contentLength", content == null ? 0 : content.length(),
                "error", error);
        emitter.complete();
    }

    private void failAndComplete(SseEmitter emitter, String conversationId, String traceId,
                                 long start, String code, String message) {
        try {
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.failed,
                    "code", code, "message", message);
            complete(emitter, conversationId, traceId, start, "", true);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void interrupted(SseEmitter emitter, String conversationId, String traceId, long start) {
        try {
            eventPublisher.publish(emitter, conversationId, traceId, OrchestrationEventType.interrupted,
                    "reason", "user_interrupt", "durationMs", System.currentTimeMillis() - start);
            conversationStoreService.updateConversationStatus(conversationId, "INTERRUPTED");
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void ensureNotInterrupted(ConversationRuntime runtime) {
        if (runtime != null && runtime.getCancellationToken() != null && runtime.getCancellationToken().isCancelled()) {
            throw new RuntimeException(INTERRUPTED_CODE);
        }
    }

    private List<String> splitText(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int step = Math.max(maxLen, 1);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += step) {
            chunks.add(text.substring(i, Math.min(text.length(), i + step)));
        }
        return chunks;
    }

    private String normalizeConversationId(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        return conversationId.trim();
    }
}
