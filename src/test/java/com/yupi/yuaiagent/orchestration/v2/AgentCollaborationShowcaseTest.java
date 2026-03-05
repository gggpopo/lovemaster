package com.yupi.yuaiagent.orchestration.v2;

import com.yupi.yuaiagent.agent.specialized.AgentRegistry;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.orchestration.core.*;
import com.yupi.yuaiagent.orchestration.v2.event.*;
import com.yupi.yuaiagent.orchestration.v2.intent.ExtendedIntentType;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentAnalyzer;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentResult;
import com.yupi.yuaiagent.router.IntentRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Agent 协作体系端到端演示测试。
 *
 * 模拟真实用户输入，展示完整流水线：
 *   用户消息 → 情绪分析 → 意图分析 → Agent 选择 → 主 Agent 执行 → 质量自检 → 记忆整理
 *
 * 所有 LLM 调用均用 Stub 替代，无需 API Key 即可运行。
 * 每个测试方法对应一种典型用户场景，日志输出可直观看到各 Agent 的协作过程。
 *
 * 运行方式：mvn test -Dtest=AgentCollaborationShowcaseTest
 */
class AgentCollaborationShowcaseTest {

    private ExecutionCoordinator coordinator;
    private StubAgentRegistry registry;
    private RecordingEventBus eventBus;

    // ======================== Stub 实现 ========================

    static class StubAgent implements SpecializedAgent {
        private final AgentType type;
        private final String responseTemplate;
        StubAgent(AgentType type, String responseTemplate) {
            this.type = type;
            this.responseTemplate = responseTemplate;
        }
        @Override public AgentType getAgentType() { return type; }
        @Override public AgentOutput execute(AgentContext context) {
            String msg = context.getMessage() != null ? context.getMessage() : "";
            String resp = responseTemplate.replace("{message}", msg);
            System.out.printf("    [%s] 收到: \"%s\" -> 回复: \"%s\"%n",
                    type, trunc(msg, 30), trunc(resp, 50));
            return AgentOutput.builder().agentType(type).blocked(false)
                    .summary(resp).data(Map.of("response", resp)).build();
        }
        static String trunc(String s, int n) {
            return s == null ? "" : s.length() > n ? s.substring(0, n) + "..." : s;
        }
    }

    static class StubEmotionAnalyst implements SpecializedAgent {
        @Override public AgentType getAgentType() { return AgentType.EMOTION_ANALYST; }
        @Override public AgentOutput execute(AgentContext context) {
            String msg = context.getMessage() != null ? context.getMessage() : "";
            Map<String, Object> p = new LinkedHashMap<>();
            if (msg.contains("死") || msg.contains("活不下去") || msg.contains("绝望")) {
                p.put("emotions", List.of(Map.of("type","DESPERATE","intensity",9,"weight",1.0)));
                p.put("dominantEmotion","DESPERATE"); p.put("overallValence",-0.9);
                p.put("maxIntensity",9.0); p.put("crisisDetected",true);
                p.put("narrativeSummary","用户表现出极度绝望情绪，检测到危机信号");
            } else if (msg.contains("焦虑") || msg.contains("难过") || msg.contains("失望") || msg.contains("委屈")) {
                p.put("emotions", List.of(Map.of("type","SAD","intensity",7,"weight",0.6),
                        Map.of("type","ANXIOUS","intensity",5,"weight",0.4)));
                p.put("dominantEmotion","SAD"); p.put("overallValence",-0.6);
                p.put("maxIntensity",8.0); p.put("crisisDetected",false);
                p.put("narrativeSummary","用户表现出较强的失落感，伴随轻微焦虑");
            } else if (msg.contains("开心") || msg.contains("期待") || msg.contains("幸福")) {
                p.put("emotions", List.of(Map.of("type","HAPPY","intensity",7,"weight",1.0)));
                p.put("dominantEmotion","HAPPY"); p.put("overallValence",0.7);
                p.put("maxIntensity",7.0); p.put("crisisDetected",false);
                p.put("narrativeSummary","用户心情愉悦，充满期待");
            } else {
                p.put("emotions", List.of(Map.of("type","CALM","intensity",3,"weight",1.0)));
                p.put("dominantEmotion","CALM"); p.put("overallValence",0.0);
                p.put("maxIntensity",3.0); p.put("crisisDetected",false);
                p.put("narrativeSummary","用户情绪平稳");
            }
            System.out.printf("    [情绪分析] %s | valence=%.1f | crisis=%s%n",
                    p.get("dominantEmotion"), ((Number)p.get("overallValence")).doubleValue(), p.get("crisisDetected"));
            return AgentOutput.builder().agentType(getAgentType()).blocked(false)
                    .summary((String)p.get("narrativeSummary")).data(p).build();
        }
    }

    static class StubReflection implements SpecializedAgent {
        @Override public AgentType getAgentType() { return AgentType.REFLECTION; }
        @Override public AgentOutput execute(AgentContext context) {
            String orig = String.valueOf(context.getSharedState().getOrDefault("originalResponse",""));
            double score = orig.length() > 20 ? 8.5 : 5.0;
            boolean refine = score < 7.0;
            String refined = refine ? orig + "（我理解你的感受，你不是一个人在面对这些。）" : orig;
            System.out.printf("    [质量自检] 评分=%.1f | 需优化=%s%n", score, refine);
            return AgentOutput.builder().agentType(getAgentType()).blocked(false)
                    .summary("质量评估完成").data(Map.of("qualityScore",score,
                            "refinedResponse",refined,"wasRefined",refine)).build();
        }
    }

    static class StubAgentRegistry extends AgentRegistry {
        private final Map<AgentType, SpecializedAgent> m = new EnumMap<>(AgentType.class);
        void register(AgentType t, SpecializedAgent a) { m.put(t, a); }
        @Override public Optional<SpecializedAgent> getAgent(AgentType t) { return Optional.ofNullable(m.get(t)); }
    }

    static class RecordingEventBus extends AgentEventBus {
        final List<AgentEvent> events = new CopyOnWriteArrayList<>();
        @Override public void publish(AgentEvent e) {
            events.add(e);
            System.out.printf("    [事件] %s%n", e.getClass().getSimpleName());
        }
        @Override public void publishAsync(AgentEvent e) { publish(e); }
    }

    static class StubIntentRouter extends IntentRouter {
        StubIntentRouter() { super(null); }
        @Override public RouteResult classify(String msg) {
            if (msg == null) msg = "";
            if (msg.contains("约会") || msg.contains("餐厅"))
                return new RouteResult(IntentType.DATE_PLANNING, 0.9, Set.of("dateLocation"), "standard", 0.5f);
            if (msg.contains("分手") || msg.contains("挽回") || msg.contains("冷战") || msg.contains("吵架"))
                return new RouteResult(IntentType.EMOTION_SUPPORT, 0.9, Set.of(), "careful", 0.3f);
            if (msg.contains("表白") || msg.contains("情话"))
                return new RouteResult(IntentType.LOVE_COPYWRITING, 0.9, Set.of(), "standard", 0.7f);
            return new RouteResult(IntentType.CHITCHAT, 0.85, Set.of(), "fast", 0.5f);
        }
    }

    // ======================== setUp ========================

    @BeforeEach
    void setUp() throws Exception {
        coordinator = new ExecutionCoordinator();
        registry = new StubAgentRegistry();
        eventBus = new RecordingEventBus();

        // 注册所有 Stub Agent
        registry.register(AgentType.EMOTION_ANALYST, new StubEmotionAnalyst());
        registry.register(AgentType.REFLECTION, new StubReflection());
        registry.register(AgentType.SAFETY, new StubAgent(AgentType.SAFETY,
                "我注意到你现在可能正在经历一些困难。如果你有伤害自己的想法，请立即拨打心理援助热线：400-161-9995"));
        registry.register(AgentType.COMPANION, new StubAgent(AgentType.COMPANION,
                "我听到你说的了，这种感受一定很不容易。能跟我多说说发生了什么吗？我一直在这里陪着你。"));
        registry.register(AgentType.DATE_PLANNER, new StubAgent(AgentType.DATE_PLANNER,
                "根据你们的情况，我推荐以下约会方案：1) 安静的咖啡馆聊天 2) 公园散步 3) 一起做饭。你觉得哪个更合适？"));
        registry.register(AgentType.CONFLICT_MEDIATOR, new StubAgent(AgentType.CONFLICT_MEDIATOR,
                "我理解你们之间出现了一些摩擦。冷静下来后，试着用'我感到...'的句式表达你的感受，而不是指责对方。"));
        registry.register(AgentType.NARRATIVE, new StubAgent(AgentType.NARRATIVE,
                "你好呀！今天过得怎么样？有什么想聊的都可以跟我说哦~"));

        // 创建 IntentAnalyzer 并注入 StubIntentRouter
        IntentAnalyzer intentAnalyzer = new IntentAnalyzer();
        inject(intentAnalyzer, "intentRouter", new StubIntentRouter());

        // 创建 AgentSelector 并注入 registry
        AgentSelector agentSelector = new AgentSelector();
        inject(agentSelector, "agentRegistry", registry);

        // 注入 ExecutionCoordinator 的所有依赖
        inject(coordinator, "agentRegistry", registry);
        inject(coordinator, "intentAnalyzer", intentAnalyzer);
        inject(coordinator, "agentSelector", agentSelector);
        inject(coordinator, "eventBus", eventBus);
        inject(coordinator, "agentExecutor", Executors.newFixedThreadPool(2));
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private AgentContext buildContext(String message) {
        return AgentContext.builder()
                .conversationId("test_conv_" + UUID.randomUUID().toString().substring(0, 6))
                .userId("test_user")
                .message(message)
                .images(List.of())
                .sharedState(new HashMap<>())
                .cancellationToken(new CancellationToken())
                .build();
    }

    // ======================== 场景测试 ========================

    /**
     * 场景1：日常闲聊
     * 用户随便聊天 → 情绪平稳 → CHITCHAT 意图 → COMPANION Agent 回复
     */
    @Test
    void scenario1_dailyChitchat() {
        System.out.println("\n========== 场景1：日常闲聊 ==========");
        System.out.println("用户: \"今天天气真好，心情不错\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("今天天气真好，心情不错"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 是否自检: %s | 质量分: %.1f%n",
                result.selectedAgent().getAgentType(), result.reflected(), result.qualityScore());

        // CHITCHAT 意图映射到 COMPANION（AgentSelector: CHITCHAT → COMPANION）
        Assertions.assertEquals(AgentType.COMPANION, result.selectedAgent().getAgentType());
        Assertions.assertFalse(result.reflected(), "日常闲聊不应触发质量自检");
        Assertions.assertFalse(result.finalResponse().isEmpty());
        assertEventPublished(EmotionAnalyzedEvent.class);
        assertEventPublished(AgentResponseEvent.class);
        assertEventPublished(MemoryConsolidationEvent.class);
    }

    /**
     * 场景2：情绪低落求安慰
     * 用户表达难过 → 高负面情绪 → COMPANION Agent（情绪优先路由）
     */
    @Test
    void scenario2_emotionalSupport() {
        System.out.println("\n========== 场景2：情绪低落求安慰 ==========");
        System.out.println("用户: \"我今天特别难过，感觉很焦虑，不知道该怎么办\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("我今天特别难过，感觉很焦虑，不知道该怎么办"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 情绪: valence=%.1f%n",
                result.selectedAgent().getAgentType(),
                ((Number) result.emotionProfile().get("overallValence")).doubleValue());

        // 高负面情绪 (valence=-0.6, intensity=8) → COMPANION 优先
        Assertions.assertEquals(AgentType.COMPANION, result.selectedAgent().getAgentType());
        Assertions.assertEquals(-0.6, ((Number) result.emotionProfile().get("overallValence")).doubleValue());
    }

    // PLACEHOLDER_MORE_TESTS

    /**
     * 场景3：约会策划
     * 用户想约会 → 情绪平稳 → DATE_PLANNING 意图 → DATE_PLANNER Agent
     */
    @Test
    void scenario3_datePlanning() {
        System.out.println("\n========== 场景3：约会策划 ==========");
        System.out.println("用户: \"周末想带女朋友去约会，有什么好的餐厅推荐吗\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("周末想带女朋友去约会，有什么好的餐厅推荐吗"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 意图: %s%n",
                result.selectedAgent().getAgentType(), result.intentResult().getExtendedIntent());

        Assertions.assertEquals(AgentType.DATE_PLANNER, result.selectedAgent().getAgentType());
        Assertions.assertEquals(ExtendedIntentType.DATE_PLANNING, result.intentResult().getExtendedIntent());
        Assertions.assertFalse(result.intentResult().isHighRisk());
    }

    /**
     * 场景4：冷战修复（高风险 → 触发质量自检）
     * 用户描述冷战 → 冲突意图 → CONFLICT_MEDIATOR Agent → ReflectionAgent 自检
     */
    @Test
    void scenario4_coldWarRepair() {
        System.out.println("\n========== 场景4：冷战修复 ==========");
        System.out.println("用户: \"我们冷战三天了，他一直不回消息，我该怎么办\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("我们冷战三天了，他一直不回消息，我该怎么办"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 意图: %s | 高风险: %s | 自检: %s | 质量分: %.1f%n",
                result.selectedAgent().getAgentType(), result.intentResult().getExtendedIntent(),
                result.intentResult().isHighRisk(), result.reflected(), result.qualityScore());

        Assertions.assertEquals(AgentType.CONFLICT_MEDIATOR, result.selectedAgent().getAgentType());
        Assertions.assertEquals(ExtendedIntentType.COLD_WAR_REPAIR, result.intentResult().getExtendedIntent());
        Assertions.assertTrue(result.intentResult().isHighRisk(), "冷战场景应标记为高风险");
        Assertions.assertTrue(result.reflected(), "高风险场景应触发质量自检");
        assertEventPublished(ReflectionCompletedEvent.class);
    }

    /**
     * 场景5：分手挽回（高风险 + 负面情绪）
     * 用户想挽回 → 分手意图 + 负面情绪 → CONFLICT_MEDIATOR → 质量自检
     */
    @Test
    void scenario5_breakupRecovery() {
        System.out.println("\n========== 场景5：分手挽回 ==========");
        System.out.println("用户: \"她跟我分手了，我很难过很失望，我想挽回她\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("她跟我分手了，我很难过很失望，我想挽回她"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 意图: %s | 情绪: %s(%.1f)%n",
                result.selectedAgent().getAgentType(), result.intentResult().getExtendedIntent(),
                result.emotionProfile().get("dominantEmotion"),
                ((Number) result.emotionProfile().get("overallValence")).doubleValue());

        Assertions.assertEquals(ExtendedIntentType.BREAKUP_RECOVERY, result.intentResult().getExtendedIntent());
        Assertions.assertTrue(result.intentResult().isHighRisk());
        Assertions.assertTrue(result.reflected());
    }

    /**
     * 场景6：危机检测（最高优先级 → SAFETY Agent）
     * 用户表达绝望 → 危机检测触发 → SAFETY Agent 介入 → 质量自检
     */
    @Test
    void scenario6_crisisDetection() {
        System.out.println("\n========== 场景6：危机检测 ==========");
        System.out.println("用户: \"我真的活不下去了，感觉一切都没有意义，太绝望了\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("我真的活不下去了，感觉一切都没有意义，太绝望了"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 危机检测: %s%n",
                result.selectedAgent().getAgentType(), result.emotionProfile().get("crisisDetected"));

        Assertions.assertEquals(AgentType.SAFETY, result.selectedAgent().getAgentType(),
                "危机场景必须路由到 SAFETY Agent");
        Assertions.assertTrue((Boolean) result.emotionProfile().get("crisisDetected"));
        assertEventPublished(CrisisDetectedEvent.class);
        // SAFETY 回复应包含求助信息
        Assertions.assertTrue(result.finalResponse().contains("热线") || result.finalResponse().contains("400"));
    }

    /**
     * 场景7：开心分享
     * 用户分享喜悦 → 正面情绪 → CHITCHAT → NARRATIVE Agent
     */
    @Test
    void scenario7_happySharing() {
        System.out.println("\n========== 场景7：开心分享 ==========");
        System.out.println("用户: \"今天他跟我表白了！我好开心好幸福！\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("今天他跟我表白了！我好开心好幸福！"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("最终回复: \"%s\"%n", StubAgent.trunc(result.finalResponse(), 80));
        System.out.printf("选中Agent: %s | 情绪: %s(valence=%.1f)%n",
                result.selectedAgent().getAgentType(),
                result.emotionProfile().get("dominantEmotion"),
                ((Number) result.emotionProfile().get("overallValence")).doubleValue());

        Assertions.assertTrue(((Number) result.emotionProfile().get("overallValence")).doubleValue() > 0,
                "正面情绪 valence 应为正");
        Assertions.assertFalse(result.reflected(), "正面闲聊不应触发自检");
    }

    /**
     * 场景8：吵架冲突
     * 用户描述吵架 → CONFLICT_RESOLUTION 意图 → CONFLICT_MEDIATOR
     */
    @Test
    void scenario8_conflictResolution() {
        System.out.println("\n========== 场景8：吵架冲突 ==========");
        System.out.println("用户: \"我们昨天吵架了，他发脾气摔门走了，我很委屈\"");
        System.out.println("--- 流水线开始 ---");

        ExecutionCoordinator.ExecutionResult result = coordinator.execute(
                buildContext("我们昨天吵架了，他发脾气摔门走了，我很委屈"));

        System.out.println("--- 流水线结束 ---");
        System.out.printf("选中Agent: %s | 意图: %s | 高风险: %s%n",
                result.selectedAgent().getAgentType(),
                result.intentResult().getExtendedIntent(),
                result.intentResult().isHighRisk());

        // 高负面情绪 (valence=-0.6, intensity=8) 触发情绪优先路由 → COMPANION
        // 即使意图是 CONFLICT_RESOLUTION，情绪优先级更高
        Assertions.assertEquals(ExtendedIntentType.CONFLICT_RESOLUTION, result.intentResult().getExtendedIntent());
        Assertions.assertEquals(AgentType.COMPANION, result.selectedAgent().getAgentType());
        Assertions.assertTrue(result.intentResult().isHighRisk());
    }

    // ======================== 事件流验证 ========================

    /**
     * 验证完整事件流：每次执行都应产生 情绪分析 → 回复 → 记忆整理 事件
     */
    @Test
    void eventFlow_shouldAlwaysEmitCoreEvents() {
        System.out.println("\n========== 事件流验证 ==========");
        coordinator.execute(buildContext("随便聊聊"));

        System.out.printf("共发布 %d 个事件: %s%n", eventBus.events.size(),
                eventBus.events.stream().map(e -> e.getClass().getSimpleName()).toList());

        assertEventPublished(EmotionAnalyzedEvent.class);
        assertEventPublished(AgentResponseEvent.class);
        assertEventPublished(MemoryConsolidationEvent.class);
    }

    private void assertEventPublished(Class<? extends AgentEvent> eventClass) {
        boolean found = eventBus.events.stream().anyMatch(eventClass::isInstance);
        Assertions.assertTrue(found, "应发布事件: " + eventClass.getSimpleName());
    }
}
