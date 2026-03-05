package com.yupi.yuaiagent.orchestration.v2.intent;

import com.yupi.yuaiagent.router.IntentRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

class IntentAnalyzerTest {

    /**
     * Stub IntentRouter that returns a fixed RouteResult without LLM.
     */
    static class StubIntentRouter extends IntentRouter {
        private final IntentRouter.IntentType fixedType;

        StubIntentRouter(IntentRouter.IntentType fixedType) {
            super(null); // no ChatModel needed
            this.fixedType = fixedType;
        }

        @Override
        public RouteResult classify(String message) {
            return new RouteResult(fixedType, 0.9, Set.of("dateLocation"), "standard", 0.5f);
        }
    }

    private IntentAnalyzer createAnalyzer(IntentRouter.IntentType baseType) throws Exception {
        IntentAnalyzer analyzer = new IntentAnalyzer();
        Field field = IntentAnalyzer.class.getDeclaredField("intentRouter");
        field.setAccessible(true);
        field.set(analyzer, new StubIntentRouter(baseType));
        return analyzer;
    }

    @Test
    void analyze_shouldDetectBreakupKeyword() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.EMOTION_SUPPORT);
        IntentResult result = analyzer.analyze("我们分手了，我想挽回");

        Assertions.assertEquals(ExtendedIntentType.BREAKUP_RECOVERY, result.getExtendedIntent());
        Assertions.assertTrue(result.isHighRisk());
        Assertions.assertTrue(result.isEmotionAware());
        Assertions.assertEquals(0.3f, result.getTemperature());
        // Conflict scenarios should clear tools
        Assertions.assertTrue(result.getSuggestedTools().isEmpty());
    }

    @Test
    void analyze_shouldDetectColdWarKeyword() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.EMOTION_SUPPORT);
        IntentResult result = analyzer.analyze("他已读不回，我们冷战三天了");

        Assertions.assertEquals(ExtendedIntentType.COLD_WAR_REPAIR, result.getExtendedIntent());
        Assertions.assertTrue(result.isHighRisk());
    }

    @Test
    void analyze_shouldDetectConflictKeyword() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.EMOTION_SUPPORT);
        IntentResult result = analyzer.analyze("我们吵架了，他发脾气摔门走了");

        Assertions.assertEquals(ExtendedIntentType.CONFLICT_RESOLUTION, result.getExtendedIntent());
        Assertions.assertTrue(result.isHighRisk());
    }

    @Test
    void analyze_breakupPriorityOverColdWar() throws Exception {
        // Message contains both breakup and cold war keywords; breakup should win
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.EMOTION_SUPPORT);
        IntentResult result = analyzer.analyze("分手后他不回消息冷战");

        Assertions.assertEquals(ExtendedIntentType.BREAKUP_RECOVERY, result.getExtendedIntent());
    }

    @Test
    void analyze_noConflictKeywords_shouldMapDirectly() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.DATE_PLANNING);
        IntentResult result = analyzer.analyze("帮我推荐一个约会地点");

        Assertions.assertEquals(ExtendedIntentType.DATE_PLANNING, result.getExtendedIntent());
        Assertions.assertFalse(result.isHighRisk());
        // DATE_PLANNING should keep tools
        Assertions.assertFalse(result.getSuggestedTools().isEmpty());
    }

    @Test
    void analyze_chitchat_shouldNotBeEmotionAware() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.CHITCHAT);
        IntentResult result = analyzer.analyze("今天天气不错");

        Assertions.assertEquals(ExtendedIntentType.CHITCHAT, result.getExtendedIntent());
        Assertions.assertFalse(result.isEmotionAware());
        Assertions.assertFalse(result.isHighRisk());
    }

    @Test
    void analyze_unsafe_shouldBeHighRisk() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.UNSAFE);
        IntentResult result = analyzer.analyze("一些不安全的内容");

        Assertions.assertEquals(ExtendedIntentType.UNSAFE, result.getExtendedIntent());
        Assertions.assertTrue(result.isHighRisk());
        Assertions.assertFalse(result.isEmotionAware());
    }

    @Test
    void analyze_emotionSupport_shouldBeEmotionAware() throws Exception {
        IntentAnalyzer analyzer = createAnalyzer(IntentRouter.IntentType.EMOTION_SUPPORT);
        IntentResult result = analyzer.analyze("我今天心情不好");

        Assertions.assertEquals(ExtendedIntentType.EMOTION_SUPPORT, result.getExtendedIntent());
        Assertions.assertTrue(result.isEmotionAware());
    }
}
