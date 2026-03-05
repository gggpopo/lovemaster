package com.yupi.yuaiagent.orchestration.v2;

import com.yupi.yuaiagent.agent.specialized.AgentRegistry;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.agent.specialized.StreamableAgent;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.v2.event.*;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentAnalyzer;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 执行协调器：管理 Agent 执行流水线。
 *
 * 流程：
 * 1. 并行执行 EmotionAnalyst + IntentAnalyzer
 * 2. AgentSelector 选择主 Agent
 * 3. 主 Agent 执行
 * 4. ReflectionAgent 质量自检（高风险场景）
 * 5. MemoryCurator 异步整理记忆
 */
@Slf4j
@Component
public class ExecutionCoordinator {

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private IntentAnalyzer intentAnalyzer;

    @Resource
    private AgentSelector agentSelector;

    @Resource
    private AgentEventBus eventBus;

    @Resource(name = "agentExecutor")
    private java.util.concurrent.Executor agentExecutor;

    /**
     * 执行结果。
     */
    public record ExecutionResult(
            IntentResult intentResult,
            Map<String, Object> emotionProfile,
            SpecializedAgent selectedAgent,
            AgentOutput mainOutput,
            String finalResponse,
            boolean reflected,
            double qualityScore
    ) {}

    /**
     * 同步执行完整流水线。
     */
    public ExecutionResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String conversationId = context.getConversationId();
        String traceId = extractTraceId(context);

        log.info("[ExecutionCoordinator-execute] conversationId={}, messageLength={}",
                conversationId, context.getMessage() == null ? 0 : context.getMessage().length());

        // 1. 并行：情绪分析 + 意图分析
        CompletableFuture<Map<String, Object>> emotionFuture = CompletableFuture.supplyAsync(() ->
                analyzeEmotion(context), agentExecutor);
        CompletableFuture<IntentResult> intentFuture = CompletableFuture.supplyAsync(() ->
                intentAnalyzer.analyze(context.getMessage()), agentExecutor);

        Map<String, Object> emotionProfile;
        IntentResult intentResult;
        try {
            emotionProfile = emotionFuture.get(10, TimeUnit.SECONDS);
            intentResult = intentFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[ExecutionCoordinator-execute] parallel analysis failed, using defaults, conversationId={}",
                    conversationId, e);
            emotionProfile = emotionFuture.getNow(Map.of("label", "neutral", "confidence", 0.5));
            intentResult = intentFuture.getNow(null);
            if (intentResult == null) {
                intentResult = intentAnalyzer.analyze(context.getMessage());
            }
        }

        // 注入情绪画像到上下文
        context.setEmotionProfile(emotionProfile);

        // 发布情绪分析事件
        eventBus.publish(new EmotionAnalyzedEvent(this, conversationId, traceId, emotionProfile));

        // 检查危机
        if (Boolean.TRUE.equals(emotionProfile.get("crisisDetected"))) {
            CrisisSeverity severity;
            try {
                severity = CrisisSeverity.valueOf(String.valueOf(emotionProfile.getOrDefault("severity", "MEDIUM")));
            } catch (IllegalArgumentException e) {
                severity = CrisisSeverity.MEDIUM;
            }
            eventBus.publish(new CrisisDetectedEvent(this, conversationId, traceId, emotionProfile, severity));
        }

        // 检查中断
        checkCancellation(context);

        // 2. 选择主 Agent
        SpecializedAgent mainAgent = agentSelector.select(intentResult, emotionProfile);
        log.info("[ExecutionCoordinator-execute] selectedAgent={}, conversationId={}",
                mainAgent.getAgentType(), conversationId);

        // 检查中断
        checkCancellation(context);

        // 3. 执行主 Agent
        AgentOutput mainOutput = mainAgent.execute(context);
        String response = extractResponse(mainOutput);

        // 发布回复事件
        eventBus.publish(new AgentResponseEvent(this, conversationId, traceId,
                mainAgent.getAgentType(), response, intentResult.isHighRisk()));

        // 4. ReflectionAgent 质量自检（高风险场景）
        boolean reflected = false;
        double qualityScore = 1.0;
        if (intentResult.isHighRisk()) {
            ReflectionOutcome outcome = runReflection(context, conversationId, traceId,
                    response, intentResult, mainAgent.getAgentType(), emotionProfile);
            if (outcome != null) {
                response = outcome.response;
                reflected = outcome.reflected;
                qualityScore = outcome.qualityScore;

                eventBus.publish(new ReflectionCompletedEvent(this, conversationId, traceId,
                        mainOutput.getSummary(), response, qualityScore, reflected));
            }
        }

        // 5. 异步触发记忆整理
        eventBus.publishAsync(new MemoryConsolidationEvent(this, conversationId, traceId, List.of()));

        long cost = System.currentTimeMillis() - start;
        log.info("[ExecutionCoordinator-execute] completed, conversationId={}, agent={}, reflected={}, qualityScore={}, costMs={}",
                conversationId, mainAgent.getAgentType(), reflected, qualityScore, cost);

        return new ExecutionResult(intentResult, emotionProfile, mainAgent, mainOutput, response, reflected, qualityScore);
    }

    /**
     * 流式执行（返回 Flux）。
     * 如果主 Agent 支持流式，返回流式输出；否则将同步结果包装为 Flux。
     */
    public Flux<String> executeStream(AgentContext context) {
        String conversationId = context.getConversationId();
        String traceId = extractTraceId(context);

        log.info("[ExecutionCoordinator-executeStream] conversationId={}, messageLength={}",
                conversationId, context.getMessage() == null ? 0 : context.getMessage().length());

        // 并行分析
        Map<String, Object> emotionProfile = analyzeEmotion(context);
        IntentResult intentResult = intentAnalyzer.analyze(context.getMessage());
        context.setEmotionProfile(emotionProfile);

        eventBus.publish(new EmotionAnalyzedEvent(this, conversationId, traceId, emotionProfile));

        // 检查危机
        if (Boolean.TRUE.equals(emotionProfile.get("crisisDetected"))) {
            CrisisSeverity severity;
            try {
                severity = CrisisSeverity.valueOf(String.valueOf(emotionProfile.getOrDefault("severity", "MEDIUM")));
            } catch (IllegalArgumentException e) {
                severity = CrisisSeverity.MEDIUM;
            }
            eventBus.publish(new CrisisDetectedEvent(this, conversationId, traceId, emotionProfile, severity));
        }

        // 选择 Agent
        SpecializedAgent mainAgent = agentSelector.select(intentResult, emotionProfile);
        log.info("[ExecutionCoordinator-executeStream] selectedAgent={}, conversationId={}",
                mainAgent.getAgentType(), conversationId);

        // 如果支持流式
        if (mainAgent instanceof StreamableAgent streamable) {
            return streamable.executeStream(context)
                    .doOnComplete(() -> eventBus.publishAsync(
                            new MemoryConsolidationEvent(this, conversationId, traceId, List.of())));
        }

        // 否则同步执行后包装
        AgentOutput output = mainAgent.execute(context);
        String response = extractResponse(output);

        // 异步记忆整理
        eventBus.publishAsync(new MemoryConsolidationEvent(this, conversationId, traceId, List.of()));

        return Flux.just(response);
    }

    /**
     * 执行情绪分析。
     */
    private Map<String, Object> analyzeEmotion(AgentContext context) {
        Optional<SpecializedAgent> emotionAnalyst = agentRegistry.getAgent(AgentType.EMOTION_ANALYST);
        if (emotionAnalyst.isEmpty()) {
            // 回退到旧的 EMOTION Agent
            emotionAnalyst = agentRegistry.getAgent(AgentType.EMOTION);
        }
        if (emotionAnalyst.isPresent()) {
            AgentOutput output = emotionAnalyst.get().execute(context);
            if (output.getData() != null) {
                return output.getData();
            }
        }
        return Map.of("label", "neutral", "confidence", 0.5);
    }

    /**
     * 从 AgentOutput 中提取回复文本。
     */
    private String extractResponse(AgentOutput output) {
        if (output.getData() != null && output.getData().containsKey("response")) {
            return String.valueOf(output.getData().get("response"));
        }
        return output.getSummary() != null ? output.getSummary() : "";
    }

    /**
     * 提取 traceId。
     */
    private String extractTraceId(AgentContext context) {
        if (context.getSharedState() != null) {
            return String.valueOf(context.getSharedState().getOrDefault("traceId", ""));
        }
        return "";
    }

    /**
     * 执行 ReflectionAgent 质量自检。
     */
    private ReflectionOutcome runReflection(AgentContext context, String conversationId, String traceId,
                                            String response, IntentResult intentResult,
                                            AgentType mainAgentType, Map<String, Object> emotionProfile) {
        Optional<SpecializedAgent> reflectionAgent = agentRegistry.getAgent(AgentType.REFLECTION);
        if (reflectionAgent.isEmpty()) {
            return null;
        }

        AgentContext reflectionContext = AgentContext.builder()
                .conversationId(conversationId)
                .userId(context.getUserId())
                .message(response)
                .emotionProfile(emotionProfile)
                .sharedState(Map.of(
                        "originalResponse", response,
                        "intent", intentResult.getExtendedIntent().name(),
                        "agentType", mainAgentType.name()
                ))
                .cancellationToken(context.getCancellationToken())
                .build();

        AgentOutput reflectionOutput = reflectionAgent.get().execute(reflectionContext);

        String refinedResponse = response;
        boolean reflected = false;
        double qualityScore = 1.0;

        if (reflectionOutput.getData() != null) {
            Object refined = reflectionOutput.getData().get("refinedResponse");
            Object score = reflectionOutput.getData().get("qualityScore");
            if (refined != null) {
                refinedResponse = String.valueOf(refined);
                reflected = true;
            }
            if (score instanceof Number s) {
                qualityScore = s.doubleValue();
            }
        }

        log.info("[ExecutionCoordinator-reflection] conversationId={}, reflected={}, qualityScore={}",
                conversationId, reflected, qualityScore);

        return new ReflectionOutcome(refinedResponse, reflected, qualityScore);
    }

    private void checkCancellation(AgentContext context) {
        if (context.getCancellationToken() != null && context.getCancellationToken().isCancelled()) {
            log.warn("[ExecutionCoordinator-checkCancellation] cancelled, conversationId={}",
                    context.getConversationId());
            throw new RuntimeException("__INTERRUPTED__");
        }
    }

    /**
     * Reflection 结果内部记录。
     */
    private record ReflectionOutcome(String response, boolean reflected, double qualityScore) {}
}
