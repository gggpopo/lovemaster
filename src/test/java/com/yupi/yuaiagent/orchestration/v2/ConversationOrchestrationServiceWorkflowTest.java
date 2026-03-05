package com.yupi.yuaiagent.orchestration.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.specialized.AssetAgent;
import com.yupi.yuaiagent.agent.specialized.EmotionAgent;
import com.yupi.yuaiagent.agent.specialized.MemoryAgent;
import com.yupi.yuaiagent.agent.specialized.NarrativeAgent;
import com.yupi.yuaiagent.agent.specialized.PersonaAgent;
import com.yupi.yuaiagent.agent.specialized.SafetyAgent;
import com.yupi.yuaiagent.conversation.model.ConversationEntity;
import com.yupi.yuaiagent.conversation.model.MessageEntity;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.core.ConversationRuntimeRegistry;
import com.yupi.yuaiagent.orchestration.core.OrchestrationEventType;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.router.IntentRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class ConversationOrchestrationServiceWorkflowTest {

    private RecordingEventPublisher eventPublisher;
    private ConversationStoreService conversationStoreService;

    private StubIntentRouter intentRouter;
    private StubPolicyResolver policyResolver;
    private StubSafetyAgent safetyAgent;
    private StubEmotionAgent emotionAgent;
    private StubMemoryAgent memoryAgent;
    private StubPersonaAgent personaAgent;
    private StubNarrativeAgent narrativeAgent;
    private StubAssetAgent assetAgent;

    private ConversationOrchestrationService service;

    @BeforeEach
    void setUp() {
        eventPublisher = new RecordingEventPublisher();

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        conversationStoreService = new ConversationStoreService(beanFactory.getBeanProvider(JdbcTemplate.class), new ObjectMapper());

        intentRouter = new StubIntentRouter();
        policyResolver = new StubPolicyResolver();
        safetyAgent = new StubSafetyAgent();
        emotionAgent = new StubEmotionAgent();
        memoryAgent = new StubMemoryAgent();
        personaAgent = new StubPersonaAgent();
        narrativeAgent = new StubNarrativeAgent();
        assetAgent = new StubAssetAgent();

        service = new ConversationOrchestrationService(
                conversationStoreService,
                intentRouter,
                policyResolver,
                safetyAgent,
                emotionAgent,
                memoryAgent,
                personaAgent,
                narrativeAgent,
                assetAgent,
                eventPublisher,
                new ConversationRuntimeRegistry()
        );
        ReflectionTestUtils.setField(service, "sseTimeoutMs", 300_000L);
    }

    @Test
    void streamConversation_shouldEmitOrderedMultiAgentWorkflow_forChatMode() throws InterruptedException {
        intentRouter.setNextResult(new IntentRouter.RouteResult(
                IntentRouter.IntentType.RELATIONSHIP_QA,
                0.96,
                Set.of(),
                "standard",
                0.5f
        ));
        policyResolver.setNextPolicy(policy(ExecutionMode.CHAT, "complex_relationship_task", Set.of()));
        narrativeAgent.setNextFlux(Flux.just("第一步先沟通目标。", "第二步约定反馈节奏。"));

        service.streamConversation("conv_chat_flow", request("请帮我拆解复合沟通计划", "agent"));

        Assertions.assertTrue(eventPublisher.awaitTerminal(5), "预期应在超时前收到终态事件");

        List<OrchestrationEventType> eventTypes = eventPublisher.getEventTypes();
        assertSubsequence(eventTypes, List.of(
                OrchestrationEventType.orchestration_started,
                OrchestrationEventType.intent_classified,
                OrchestrationEventType.policy_selected,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.response_chunk,
                OrchestrationEventType.response_completed
        ));

        List<String> startedAgents = eventPublisher.payloadsOf(OrchestrationEventType.agent_started).stream()
                .map(payload -> String.valueOf(payload.get("agent")))
                .toList();
        Assertions.assertEquals(List.of("SAFETY", "EMOTION", "MEMORY", "PERSONA", "NARRATIVE"), startedAgents);

        Assertions.assertEquals(0, assetAgent.getRunToolChainCalls(), "CHAT 模式不应走资产工具链");

        List<MessageEntity> messages = conversationStoreService.listRecentMessages("conv_chat_flow", 10);
        MessageEntity last = messages.get(messages.size() - 1);
        Assertions.assertEquals("assistant", last.getRole());
        Assertions.assertEquals("第一步先沟通目标。第二步约定反馈节奏。", last.getContent());
    }

    @Test
    void streamConversation_shouldEmitToolEventsAndUseSharedState_forToolMode() throws InterruptedException {
        intentRouter.setNextResult(new IntentRouter.RouteResult(
                IntentRouter.IntentType.DATE_PLANNING,
                0.92,
                Set.of("dateLocation"),
                "standard",
                0.3f
        ));
        policyResolver.setNextPolicy(policy(ExecutionMode.TOOL, "intent_requires_tools", Set.of("dateLocation")));
        assetAgent.setToolResult("推荐地点：外滩附近餐厅。");

        service.streamConversation("conv_tool_flow", request("推荐一个上海约会餐厅", "tool"));

        Assertions.assertTrue(eventPublisher.awaitTerminal(5), "预期应在超时前收到终态事件");

        List<OrchestrationEventType> eventTypes = eventPublisher.getEventTypes();
        assertSubsequence(eventTypes, List.of(
                OrchestrationEventType.orchestration_started,
                OrchestrationEventType.intent_classified,
                OrchestrationEventType.policy_selected,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.agent_started,
                OrchestrationEventType.agent_finished,
                OrchestrationEventType.tool_call_started,
                OrchestrationEventType.response_chunk,
                OrchestrationEventType.tool_call_finished,
                OrchestrationEventType.response_completed
        ));

        Assertions.assertEquals(0, narrativeAgent.getStreamCalls(), "TOOL 模式不应走叙事流式输出");
        Assertions.assertEquals(1, assetAgent.getRunToolChainCalls(), "TOOL 模式应走一次资产工具链");
        Assertions.assertNotNull(assetAgent.getLastSystemPrompt());
        Assertions.assertTrue(assetAgent.getLastSystemPrompt().contains("人格约束"), "systemPrompt 应包含 persona 信息");
        Assertions.assertTrue(assetAgent.getLastSystemPrompt().contains("用户当前情绪"), "systemPrompt 应包含 emotion 信息");
        Assertions.assertTrue(assetAgent.getLastSystemPrompt().contains("可参考历史消息条数"), "systemPrompt 应包含 memory 信息");
    }

    @Test
    void streamConversation_shouldEmitInterruptedEvent_whenRuntimeInterrupted() throws InterruptedException {
        intentRouter.setNextResult(new IntentRouter.RouteResult(
                IntentRouter.IntentType.RELATIONSHIP_QA,
                0.95,
                Set.of(),
                "standard",
                0.5f
        ));
        policyResolver.setNextPolicy(policy(ExecutionMode.CHAT, "complex_relationship_task", Set.of()));
        narrativeAgent.setNextFlux(
                Flux.interval(Duration.ofMillis(40))
                        .map(index -> "chunk-" + index)
                        .take(30)
        );

        service.streamConversation("conv_interrupt_flow", request("给我一个长期关系维护计划", "agent"));

        boolean interrupted = false;
        for (int i = 0; i < 10; i++) {
            interrupted = service.interrupt("conv_interrupt_flow", "manual_stop");
            if (interrupted) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        Assertions.assertTrue(interrupted, "应能成功中断运行中的会话");
        Assertions.assertTrue(eventPublisher.awaitTerminal(5), "中断后应收到终态事件");

        List<OrchestrationEventType> eventTypes = eventPublisher.getEventTypes();
        Assertions.assertTrue(eventTypes.contains(OrchestrationEventType.interrupted), "应发布 interrupted 事件");
        Assertions.assertFalse(eventTypes.contains(OrchestrationEventType.response_completed), "中断分支不应发布 response_completed");

        ConversationEntity conversationEntity = conversationStoreService.getConversation("conv_interrupt_flow");
        Assertions.assertNotNull(conversationEntity);
        Assertions.assertEquals("INTERRUPTED", conversationEntity.getStatus());
    }

    private static OrchestrationPolicy policy(ExecutionMode mode, String reason, Set<String> tools) {
        return OrchestrationPolicy.builder()
                .mode(mode)
                .reason(reason)
                .modelProfile("standard")
                .temperature(0.5f)
                .suggestedTools(tools)
                .build();
    }

    private static ConversationMessageRequest request(String message, String forceMode) {
        ConversationMessageRequest request = new ConversationMessageRequest();
        request.setUserId("user_test");
        request.setPersonaId("default");
        request.setSceneId("general_relationship");
        request.setMessage(message);
        request.setForceMode(forceMode);
        return request;
    }

    private static AgentOutput output(AgentType type, String summary, Map<String, Object> data) {
        return AgentOutput.builder()
                .agentType(type)
                .blocked(false)
                .summary(summary)
                .data(data)
                .build();
    }

    private static void assertSubsequence(List<OrchestrationEventType> actual, List<OrchestrationEventType> expectedOrdered) {
        int cursor = 0;
        for (OrchestrationEventType eventType : actual) {
            if (cursor < expectedOrdered.size() && eventType == expectedOrdered.get(cursor)) {
                cursor++;
            }
        }
        Assertions.assertEquals(expectedOrdered.size(), cursor,
                "事件顺序不符合预期。actual=" + actual + ", expectedSubsequence=" + expectedOrdered);
    }

    /**
     * 测试专用事件发布器：只记录事件，不真正向 SseEmitter 写数据，便于稳定观察流程。
     */
    private static class RecordingEventPublisher extends OrchestrationEventPublisher {

        private final List<OrchestrationEventType> eventTypes = new ArrayList<>();
        private final Map<OrchestrationEventType, List<Map<String, Object>>> payloadByType = new LinkedHashMap<>();
        private final CountDownLatch terminalLatch = new CountDownLatch(1);

        private RecordingEventPublisher() {
            super(new ObjectMapper());
        }

        @Override
        public synchronized void publish(SseEmitter emitter,
                                         String conversationId,
                                         String traceId,
                                         OrchestrationEventType type,
                                         Map<String, Object> payload) {
            eventTypes.add(type);
            payloadByType.computeIfAbsent(type, key -> new ArrayList<>())
                    .add(payload == null ? Map.of() : new LinkedHashMap<>(payload));
            if (type == OrchestrationEventType.response_completed
                    || type == OrchestrationEventType.failed
                    || type == OrchestrationEventType.interrupted) {
                terminalLatch.countDown();
            }
        }

        @Override
        public synchronized void publish(SseEmitter emitter,
                                         String conversationId,
                                         String traceId,
                                         OrchestrationEventType type,
                                         Object... kv) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (kv != null) {
                for (int i = 0; i + 1 < kv.length; i += 2) {
                    payload.put(String.valueOf(kv[i]), kv[i + 1]);
                }
            }
            publish(emitter, conversationId, traceId, type, payload);
        }

        private boolean awaitTerminal(int timeoutSeconds) throws InterruptedException {
            return terminalLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        private synchronized List<OrchestrationEventType> getEventTypes() {
            return new ArrayList<>(eventTypes);
        }

        private synchronized List<Map<String, Object>> payloadsOf(OrchestrationEventType type) {
            return new ArrayList<>(payloadByType.getOrDefault(type, List.of()));
        }
    }

    private static class StubIntentRouter extends IntentRouter {

        private RouteResult nextResult = new RouteResult(IntentType.CHITCHAT, 0.5, Set.of(), "standard", 0.5f);

        private StubIntentRouter() {
            super(null);
        }

        @Override
        public RouteResult classify(String userMessage) {
            return nextResult;
        }

        private void setNextResult(RouteResult nextResult) {
            this.nextResult = nextResult;
        }
    }

    private static class StubPolicyResolver extends DefaultPolicyResolver {

        private OrchestrationPolicy nextPolicy = policy(ExecutionMode.CHAT, "default", Set.of());

        @Override
        public OrchestrationPolicy resolve(IntentRouter.RouteResult routeResult, ConversationMessageRequest request) {
            return nextPolicy;
        }

        private void setNextPolicy(OrchestrationPolicy nextPolicy) {
            this.nextPolicy = nextPolicy;
        }
    }

    private static class StubSafetyAgent extends SafetyAgent {
        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.SAFETY, "安全检查通过", Map.of("code", "safe"));
        }
    }

    private static class StubEmotionAgent extends EmotionAgent {
        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.EMOTION, "情绪识别完成", Map.of("label", "positive", "confidence", 0.9));
        }
    }

    private static class StubMemoryAgent extends MemoryAgent {
        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.MEMORY, "历史消息召回完成", Map.of("count", 3, "snippets", List.of()));
        }
    }

    private static class StubPersonaAgent extends PersonaAgent {
        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.PERSONA, "人格约束加载完成",
                    Map.of("personaId", "default", "name", "恋爱大师", "systemPrompt", "保持温柔且可执行"));
        }
    }

    private static class StubNarrativeAgent extends NarrativeAgent {

        private Flux<String> nextFlux = Flux.just("默认回复");
        private volatile int streamCalls = 0;

        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.NARRATIVE, "开始生成回答", Map.of("ready", true));
        }

        @Override
        public Flux<String> streamResponse(AgentContext context, String systemPrompt) {
            streamCalls++;
            return nextFlux;
        }

        private void setNextFlux(Flux<String> nextFlux) {
            this.nextFlux = nextFlux;
        }

        private int getStreamCalls() {
            return streamCalls;
        }
    }

    private static class StubAssetAgent extends AssetAgent {

        private volatile int runToolChainCalls = 0;
        private volatile String toolResult = "";
        private volatile String lastSystemPrompt;

        @Override
        public AgentOutput execute(AgentContext context) {
            return output(AgentType.ASSET, "工具调用待执行", Map.of("ready", true));
        }

        @Override
        public String runToolChain(AgentContext context, String systemPrompt) {
            runToolChainCalls++;
            lastSystemPrompt = systemPrompt;
            return toolResult;
        }

        private void setToolResult(String toolResult) {
            this.toolResult = toolResult;
        }

        private int getRunToolChainCalls() {
            return runToolChainCalls;
        }

        private String getLastSystemPrompt() {
            return lastSystemPrompt;
        }
    }
}
