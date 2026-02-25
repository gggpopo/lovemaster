package com.yupi.yuaiagent.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.dto.OrchestrationChatRequest;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.orchestration.scene.SceneContext;
import com.yupi.yuaiagent.orchestration.scene.ScenePromptService;
import com.yupi.yuaiagent.orchestration.schema.AssistantResponseSchema;
import com.yupi.yuaiagent.orchestration.schema.StructuredResponseComposer;
import com.yupi.yuaiagent.router.IntentRouter;
import com.yupi.yuaiagent.tenant.RateLimitService;
import com.yupi.yuaiagent.tenant.TenantContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 统一编排服务：意图识别 + 策略决策 + 执行链路选择 + 流式事件输出。
 */
@Slf4j
@Service
public class OrchestrationService {

    @Resource
    private LoveApp loveApp;

    @Resource
    private IntentRouter intentRouter;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel chatModel;

    @Autowired
    private ObjectProvider<RateLimitService> rateLimitServiceProvider;

    @Resource
    private StructuredResponseComposer structuredResponseComposer;

    @Resource
    private ScenePromptService scenePromptService;

    @Value("${app.orchestration.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    @Value("${app.orchestration.agent-message-threshold:120}")
    private int agentMessageThreshold;

    /**
     * 统一编排入口
     */
    public SseEmitter orchestrate(OrchestrationChatRequest request) {
        final long start = System.currentTimeMillis();
        final String traceId = safeTraceId(MDC.get("traceId"));
        final String rateLimitKey = resolveRateLimitKey();

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        CompletableFuture.runAsync(() -> execute(request, emitter, traceId, rateLimitKey, start));

        emitter.onTimeout(() -> {
            log.warn("[OrchestrationService-timeout] {}",
                    kv("traceId", traceId, "timeoutMs", sseTimeoutMs));
            emitter.complete();
        });

        return emitter;
    }

    private void execute(OrchestrationChatRequest request,
                         SseEmitter emitter,
                         String traceId,
                         String rateLimitKey,
                         long start) {
        ExecutionMode mode = ExecutionMode.BLOCK;
        String chatId = normalizeChatId(request == null ? null : request.getChatId());
        String message = request == null ? "" : safeTrim(request.getMessage());
        List<String> images = request == null || request.getImages() == null ? List.of() : request.getImages();
        String requestedSceneId = request == null ? "" : safeTrim(request.getSceneId());
        SceneContext sceneContext = scenePromptService.resolve(chatId, requestedSceneId, message);

        log.info("[OrchestrationService-execute] {}",
                kv("chatId", chatId,
                        "messageLength", message.length(),
                        "imageCount", images.size(),
                        "forceMode", request == null ? "" : request.getForceMode(),
                        "requestedSceneId", requestedSceneId,
                        "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                        "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                        "sceneTurn", sceneContext == null ? 0 : sceneContext.getTurnCount(),
                        "traceId", traceId));

        try {
            checkRateLimit(rateLimitKey);

            if (StrUtil.isBlank(message) && images.isEmpty()) {
                sendEvent(emitter, "error",
                        payload("code", "INVALID_ARGUMENT", "message", "message 和 images 不能同时为空"), traceId);
                sendDone(emitter, traceId, start, chatId, ExecutionMode.BLOCK, true);
                return;
            }

            IntentRouter.RouteResult routeResult = intentRouter.classify(message);
            OrchestrationPolicy policy = resolvePolicy(routeResult, message, images, request == null ? null : request.getForceMode());
            policy = applyScenePolicy(policy, sceneContext);
            mode = policy.getMode();

            log.info("[OrchestrationService-route] {}",
                    kv("chatId", chatId,
                            "traceId", traceId,
                            "intent", routeResult.getIntentType(),
                            "confidence", routeResult.getConfidence(),
                            "modelProfile", routeResult.getModelProfile(),
                            "temperature", routeResult.getTemperature(),
                            "suggestedTools", routeResult.getSuggestedTools()));
            log.info("[OrchestrationService-policy] {}",
                    kv("chatId", chatId,
                            "traceId", traceId,
                            "mode", policy.getMode(),
                            "reason", policy.getReason(),
                            "modelProfile", policy.getModelProfile(),
                            "temperature", policy.getTemperature(),
                            "suggestedTools", policy.getSuggestedTools(),
                            "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                            "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                            "sceneTurn", sceneContext == null ? 0 : sceneContext.getTurnCount()));

            sendEvent(emitter, "route", buildRoutePayload(routeResult), traceId);
            sendEvent(emitter, "policy", buildPolicyPayload(policy), traceId);
            sendEvent(emitter, "scene", buildScenePayload(sceneContext), traceId);

            switch (policy.getMode()) {
                case BLOCK -> executeBlock(emitter, chatId, traceId, start, routeResult);
                case VISION -> executeVision(emitter, chatId, message, images, traceId, start, policy, routeResult, sceneContext);
                case TOOL -> executeTool(emitter, chatId, message, traceId, start, policy, routeResult, sceneContext);
                case AGENT -> executeAgent(emitter, chatId, message, traceId, start, policy, routeResult, sceneContext);
                case CHAT -> executeChat(emitter, chatId, message, traceId, start, policy, routeResult, sceneContext);
                default -> executeChat(emitter, chatId, message, traceId, start, policy, routeResult, sceneContext);
            }
        } catch (Exception e) {
            log.error("[OrchestrationService-execute] {}",
                    kv("chatId", chatId, "traceId", traceId, "mode", mode, "status", "error"), e);
            try {
                sendEvent(emitter, "error",
                        payload("code", "INTERNAL_ERROR", "message", StrUtil.blankToDefault(e.getMessage(), "internal error")), traceId);
            } catch (Exception ignore) {
                // ignore
            } finally {
                sendDone(emitter, traceId, start, chatId, mode, true);
            }
        }
    }

    private void executeBlock(SseEmitter emitter,
                              String chatId,
                              String traceId,
                              long start,
                              IntentRouter.RouteResult routeResult) throws IOException {
        sendStructuredResponse(
                emitter,
                chatId,
                traceId,
                routeResult,
                ExecutionMode.BLOCK,
                "我不能协助处理可能有伤害或违法风险的请求。你可以换一种安全、合法的方式描述需求，我会继续帮助你。",
                true
        );
        sendDone(emitter, traceId, start, chatId, ExecutionMode.BLOCK, false);
    }

    private void executeChat(SseEmitter emitter,
                             String chatId,
                             String message,
                             String traceId,
                             long start,
                             OrchestrationPolicy policy,
                             IntentRouter.RouteResult routeResult,
                             SceneContext sceneContext) throws IOException {
        String userPrompt = buildUserPrompt(message);
        String dynamicSystemPrompt = buildDynamicSystemPrompt(policy, sceneContext);
        log.info("[OrchestrationService-prompt] {}",
                kv("chatId", chatId,
                        "traceId", traceId,
                        "mode", ExecutionMode.CHAT,
                        "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                        "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                        "systemPromptLength", dynamicSystemPrompt.length(),
                        "userPromptLength", userPrompt.length()));
        String result = collectFluxResult(loveApp.doChatByStream(userPrompt, chatId, dynamicSystemPrompt));
        sendStructuredResponse(emitter, chatId, traceId, routeResult, ExecutionMode.CHAT, result, false);
        sendDone(emitter, traceId, start, chatId, ExecutionMode.CHAT, false);
    }

    private void executeVision(SseEmitter emitter,
                               String chatId,
                               String message,
                               List<String> images,
                               String traceId,
                               long start,
                               OrchestrationPolicy policy,
                               IntentRouter.RouteResult routeResult,
                               SceneContext sceneContext) throws IOException {
        String userPrompt = buildUserPrompt(message);
        String dynamicSystemPrompt = buildDynamicSystemPrompt(policy, sceneContext);
        log.info("[OrchestrationService-prompt] {}",
                kv("chatId", chatId,
                        "traceId", traceId,
                        "mode", ExecutionMode.VISION,
                        "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                        "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                        "systemPromptLength", dynamicSystemPrompt.length(),
                        "userPromptLength", userPrompt.length(),
                        "imageCount", images == null ? 0 : images.size()));
        String result = collectFluxResult(loveApp.doChatWithVision(userPrompt, chatId, images, dynamicSystemPrompt));
        sendStructuredResponse(emitter, chatId, traceId, routeResult, ExecutionMode.VISION, result, false);
        sendDone(emitter, traceId, start, chatId, ExecutionMode.VISION, false);
    }

    private void executeTool(SseEmitter emitter,
                             String chatId,
                             String message,
                             String traceId,
                             long start,
                             OrchestrationPolicy policy,
                             IntentRouter.RouteResult routeResult,
                             SceneContext sceneContext) throws IOException {
        Set<String> suggestedTools = policy == null ? Set.of() : scenePromptService.mergeSuggestedTools(policy.getSuggestedTools(), sceneContext);
        StringBuilder toolGuide = new StringBuilder("\n请优先使用最合适的工具，并在结尾给出可执行建议。");
        if (suggestedTools.contains("dateLocation")) {
            toolGuide.append("\n当用户咨询餐厅、店铺、景点、咖啡馆等地点推荐时，必须调用 dateLocation 工具返回地点卡片。")
                    .append("\n优先返回带图片的地点信息；如果图片无效，不要输出失效图片链接。")
                    .append("\n如果工具结果包含 <!--LOCATION_CARD:...--> 标记，必须逐字原样保留输出，禁止改写、删除或转义。")
                    .append("\n不要在正文输出 [实景图](url) 这类原始链接，优先让地点卡片承载图片。");
        }
        if (suggestedTools.contains("memoryRecall")) {
            toolGuide.append("\n当用户询问“你还记得我之前说过什么/我的预算和偏好”等历史信息时，优先调用 memoryRecall 工具。")
                    .append("\n先基于工具结果确认历史事实，再给建议，避免凭空编造记忆。");
        }

        String userPrompt = buildUserPrompt(message) + toolGuide;
        String dynamicSystemPrompt = buildDynamicSystemPrompt(policy, sceneContext);
        log.info("[OrchestrationService-prompt] {}",
                kv("chatId", chatId,
                        "traceId", traceId,
                        "mode", ExecutionMode.TOOL,
                        "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                        "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                        "systemPromptLength", dynamicSystemPrompt.length(),
                        "userPromptLength", userPrompt.length(),
                        "suggestedTools", suggestedTools));

        String result = loveApp.doChatWithTools(userPrompt, chatId, suggestedTools, dynamicSystemPrompt);
        sendStructuredResponse(emitter, chatId, traceId, routeResult, ExecutionMode.TOOL, result, false);
        sendDone(emitter, traceId, start, chatId, ExecutionMode.TOOL, false);
    }

    private void executeAgent(SseEmitter emitter,
                              String chatId,
                              String message,
                              String traceId,
                              long start,
                              OrchestrationPolicy policy,
                              IntentRouter.RouteResult routeResult,
                              SceneContext sceneContext) throws IOException {
        String dynamicSystemPrompt = buildDynamicSystemPrompt(policy, sceneContext);
        String orchestratedMessage = "【系统指令】\n"
                + dynamicSystemPrompt
                + "\n【用户输入】\n"
                + buildUserPrompt(message)
                + "\n请在可行时拆解步骤，并说明每一步的产出。";
        log.info("[OrchestrationService-prompt] {}",
                kv("chatId", chatId,
                        "traceId", traceId,
                        "mode", ExecutionMode.AGENT,
                        "sceneId", sceneContext == null ? "" : sceneContext.getSceneId(),
                        "sceneStage", sceneContext == null ? "" : sceneContext.getSceneStage(),
                        "promptLength", orchestratedMessage.length()));

        YuManus yuManus = new YuManus(allTools, chatModel);
        String result = yuManus.run(orchestratedMessage);

        sendStructuredResponse(emitter, chatId, traceId, routeResult, ExecutionMode.AGENT, result, false);
        sendDone(emitter, traceId, start, chatId, ExecutionMode.AGENT, false);
    }

    private String collectFluxResult(Flux<String> flux) {
        if (flux == null) {
            return "";
        }
        List<String> chunks = flux
                .filter(chunk -> chunk != null && !"[DONE]".equals(chunk))
                .collectList()
                .block();
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) {
            sb.append(chunk);
        }
        return sb.toString();
    }

    private void sendStructuredResponse(SseEmitter emitter,
                                        String chatId,
                                        String traceId,
                                        IntentRouter.RouteResult routeResult,
                                        ExecutionMode mode,
                                        String text,
                                        boolean blocked) throws IOException {
        String intent = routeResult == null ? "UNKNOWN" : routeResult.getIntentType().name();
        double confidence = routeResult == null ? 0.0 : routeResult.getConfidence();
        AssistantResponseSchema response = structuredResponseComposer.compose(
                chatId,
                intent,
                mode.name(),
                StrUtil.nullToDefault(text, ""),
                confidence,
                blocked
        );
        sendEvent(emitter, "structured_response", payload("chatId", chatId, "response", response), traceId);
    }

    private OrchestrationPolicy resolvePolicy(IntentRouter.RouteResult routeResult,
                                              String message,
                                              List<String> images,
                                              String forceMode) {
        ExecutionMode force = parseForceMode(forceMode);
        if (force != null) {
            return OrchestrationPolicy.builder()
                    .mode(force)
                    .reason("force_mode")
                    .modelProfile(routeResult.getModelProfile())
                    .temperature(routeResult.getTemperature())
                    .suggestedTools(routeResult.getSuggestedTools())
                    .build();
        }

        if (images != null && !images.isEmpty()) {
            return buildPolicy(ExecutionMode.VISION, "has_images", routeResult);
        }

        if (routeResult.getIntentType() == IntentRouter.IntentType.UNSAFE) {
            return buildPolicy(ExecutionMode.BLOCK, "unsafe_intent", routeResult);
        }

        if (isMemoryRecallTask(message)) {
            OrchestrationPolicy recallPolicy = buildPolicy(ExecutionMode.TOOL, "memory_recall_tool", routeResult);
            return withSuggestedTool(recallPolicy, "memoryRecall");
        }

        switch (routeResult.getIntentType()) {
            case DATE_PLANNING, GIFT_ADVICE, IMAGE_REQUEST -> {
                return buildPolicy(ExecutionMode.TOOL, "intent_requires_tools", routeResult);
            }
            case RELATIONSHIP_QA -> {
                if (isComplexTask(message)) {
                    return buildPolicy(ExecutionMode.AGENT, "complex_relationship_task", routeResult);
                }
                return buildPolicy(ExecutionMode.CHAT, "relationship_qa_chat", routeResult);
            }
            case LOVE_COPYWRITING, EMOTION_SUPPORT, CHITCHAT -> {
                return buildPolicy(ExecutionMode.CHAT, "direct_chat", routeResult);
            }
            default -> {
                return buildPolicy(ExecutionMode.CHAT, "fallback_chat", routeResult);
            }
        }
    }

    private OrchestrationPolicy buildPolicy(ExecutionMode mode, String reason, IntentRouter.RouteResult routeResult) {
        return OrchestrationPolicy.builder()
                .mode(mode)
                .reason(reason)
                .modelProfile(routeResult.getModelProfile())
                .temperature(routeResult.getTemperature())
                .suggestedTools(routeResult.getSuggestedTools())
                .build();
    }

    private OrchestrationPolicy withSuggestedTool(OrchestrationPolicy policy, String toolAlias) {
        if (policy == null || StrUtil.isBlank(toolAlias)) {
            return policy;
        }
        Set<String> mergedTools = new LinkedHashSet<>();
        if (policy.getSuggestedTools() != null) {
            mergedTools.addAll(policy.getSuggestedTools());
        }
        mergedTools.add(toolAlias.trim());
        return OrchestrationPolicy.builder()
                .mode(policy.getMode())
                .reason(policy.getReason())
                .modelProfile(policy.getModelProfile())
                .temperature(policy.getTemperature())
                .suggestedTools(mergedTools)
                .build();
    }

    private OrchestrationPolicy applyScenePolicy(OrchestrationPolicy policy, SceneContext sceneContext) {
        if (policy == null) {
            return null;
        }
        Set<String> mergedTools = scenePromptService.mergeSuggestedTools(policy.getSuggestedTools(), sceneContext);
        String reason = StrUtil.blankToDefault(policy.getReason(), "default");
        if (sceneContext != null) {
            reason = reason + "+scene(" + sceneContext.getSceneId() + ":" + sceneContext.getSceneStage() + ")";
        }
        return OrchestrationPolicy.builder()
                .mode(policy.getMode())
                .reason(reason)
                .modelProfile(policy.getModelProfile())
                .temperature(policy.getTemperature())
                .suggestedTools(mergedTools)
                .build();
    }

    private boolean isComplexTask(String message) {
        if (StrUtil.isBlank(message)) {
            return false;
        }
        if (message.length() >= agentMessageThreshold) {
            return true;
        }
        String text = message.toLowerCase();
        return text.contains("步骤")
                || text.contains("计划")
                || text.contains("执行")
                || text.contains("清单")
                || text.contains("方案")
                || text.contains("拆解");
    }

    private boolean isMemoryRecallTask(String message) {
        if (StrUtil.isBlank(message)) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("还记得")
                || text.contains("记得我")
                || text.contains("之前说过")
                || text.contains("上次说")
                || text.contains("以前聊过")
                || text.contains("我之前")
                || text.contains("你记不记得");
    }

    private String buildUserPrompt(String message) {
        return StrUtil.blankToDefault(message, "请基于当前上下文继续分析");
    }

    private String buildDynamicSystemPrompt(OrchestrationPolicy policy, SceneContext sceneContext) {
        String scenePrompt = scenePromptService.buildScenePrompt(sceneContext);
        String baseRolePrompt = "你是深耕恋爱心理领域的专家，回答需要温柔、尊重、可执行，并保持边界感。";
        if (policy == null) {
            return StrUtil.isBlank(scenePrompt) ? baseRolePrompt : baseRolePrompt + "\n" + scenePrompt;
        }
        String profile = StrUtil.blankToDefault(policy.getModelProfile(), "standard");
        String toneGuide = switch (profile) {
            case "creative" -> "请输出更有创造力的表达，并保持建议可执行。";
            case "fast" -> "请优先给出简洁直接的答案。";
            default -> "请给出结构化、可执行的建议。";
        };
        if (StrUtil.isBlank(scenePrompt)) {
            return baseRolePrompt + "\n" + toneGuide;
        }
        return baseRolePrompt + "\n" + toneGuide + "\n" + scenePrompt;
    }

    private void checkRateLimit(String key) {
        RateLimitService rateLimitService = rateLimitServiceProvider.getIfAvailable();
        if (rateLimitService == null) {
            return;
        }
        if (!rateLimitService.allowRequest(key)) {
            int remaining = rateLimitService.getRemainingRequests(key);
            log.warn("[OrchestrationService-checkRateLimit] {}",
                    kv("rateLimitKey", key, "allowed", false, "remaining", remaining));
            throw new IllegalStateException("请求过于频繁，请稍后再试。remaining=" + remaining);
        }
    }

    private Map<String, Object> buildRoutePayload(IntentRouter.RouteResult routeResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", routeResult.getIntentType().name());
        payload.put("confidence", routeResult.getConfidence());
        payload.put("suggestedTools", routeResult.getSuggestedTools());
        payload.put("modelProfile", routeResult.getModelProfile());
        payload.put("temperature", routeResult.getTemperature());
        return payload;
    }

    private Map<String, Object> buildPolicyPayload(OrchestrationPolicy policy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", policy.getMode().name());
        payload.put("reason", policy.getReason());
        payload.put("modelProfile", policy.getModelProfile());
        payload.put("temperature", policy.getTemperature());
        payload.put("suggestedTools", policy.getSuggestedTools());
        return payload;
    }

    private Map<String, Object> buildScenePayload(SceneContext sceneContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (sceneContext == null) {
            payload.put("sceneId", "");
            payload.put("sceneName", "");
            payload.put("sceneStage", "");
            payload.put("turnCount", 0);
            return payload;
        }
        payload.put("sceneId", sceneContext.getSceneId());
        payload.put("sceneName", sceneContext.getSceneName());
        payload.put("sceneStage", sceneContext.getSceneStage() == null ? "" : sceneContext.getSceneStage().name());
        payload.put("turnCount", sceneContext.getTurnCount());
        return payload;
    }

    private void sendDone(SseEmitter emitter,
                          String traceId,
                          long start,
                          String chatId,
                          ExecutionMode mode,
                          boolean hasError) {
        long cost = System.currentTimeMillis() - start;
        try {
            sendEvent(emitter, "done", payload(
                    "chatId", chatId,
                    "mode", mode.name(),
                    "durationMs", cost,
                    "error", hasError
            ), traceId);
            log.info("[OrchestrationService-done] {}",
                    kv("chatId", chatId, "mode", mode, "durationMs", cost, "traceId", traceId, "error", hasError));
        } catch (Exception e) {
            log.warn("[OrchestrationService-done] {}",
                    kv("chatId", chatId, "traceId", traceId, "status", "send_done_failed"), e);
        } finally {
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter,
                           String type,
                           Map<String, Object> payload,
                           String traceId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("timestamp", System.currentTimeMillis());
        event.put("traceId", traceId);
        event.put("payload", payload);

        try {
            emitter.send(JSONUtil.toJsonStr(event));
        } catch (IOException e) {
            throw new RuntimeException("send sse event failed", e);
        }
    }

    private ExecutionMode parseForceMode(String forceMode) {
        if (StrUtil.isBlank(forceMode)) {
            return null;
        }
        String mode = forceMode.trim().toUpperCase();
        try {
            return ExecutionMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveRateLimitKey() {
        TenantContext.TenantInfo tenantInfo = TenantContext.getTenant();
        return tenantInfo == null ? "anonymous" : tenantInfo.getRateLimitKey();
    }

    private String normalizeChatId(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            return "orch_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return chatId.trim();
    }

    private String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeTraceId(String traceId) {
        return StrUtil.isBlank(traceId) ? "-" : traceId;
    }

    private Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
