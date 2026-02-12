package com.yupi.yuaiagent.router;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IntentRouterTest {

    static class FixedChatModel implements ChatModel {
        private final String reply;
        private final AtomicInteger calls = new AtomicInteger(0);

        FixedChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }

        int getCalls() {
            return calls.get();
        }
    }

    @Test
    void keywordMatch_shouldReturnDirectly_andNotCallLlm() {
        FixedChatModel chatModel = new FixedChatModel("CHITCHAT|0.99");
        IntentRouter router = new IntentRouter(chatModel);

        IntentRouter.RouteResult r1 = router.classify("给我推荐约会餐厅");
        assertEquals(IntentRouter.IntentType.DATE_PLANNING, r1.getIntentType());
        assertTrue(r1.getConfidence() >= 0.9);
        assertEquals(Set.of("dateLocation", "weather"), r1.getSuggestedTools());

        IntentRouter.RouteResult r2 = router.classify("帮我写一段表白文案");
        assertEquals(IntentRouter.IntentType.LOVE_COPYWRITING, r2.getIntentType());
        assertEquals("creative", r2.getModelProfile());

        IntentRouter.RouteResult r3 = router.classify("我要找图 壁纸");
        assertEquals(IntentRouter.IntentType.IMAGE_REQUEST, r3.getIntentType());
        assertEquals(Set.of("imageSearch"), r3.getSuggestedTools());

        // 关键词命中，不应触发 LLM
        assertEquals(0, chatModel.getCalls());
    }

    @Test
    void unsafe_shouldShortCircuit_andNeverCallLlm() {
        FixedChatModel chatModel = new FixedChatModel("DATE_PLANNING|0.9");
        IntentRouter router = new IntentRouter(chatModel);

        IntentRouter.RouteResult r = router.classify("你妈的，去死");
        assertEquals(IntentRouter.IntentType.UNSAFE, r.getIntentType());
        assertEquals(1.0, r.getConfidence());
        assertEquals(0, chatModel.getCalls());
    }

    @Test
    void llmFallback_shouldParseCategoryAndConfidence() {
        FixedChatModel chatModel = new FixedChatModel("EMOTION_SUPPORT|0.85");
        IntentRouter router = new IntentRouter(chatModel);

        IntentRouter.RouteResult r = router.classify("我感觉很难过，不知道怎么办");
        assertEquals(IntentRouter.IntentType.EMOTION_SUPPORT, r.getIntentType());
        assertEquals(1, chatModel.getCalls());
        assertEquals(Set.of("emotionDetection"), r.getSuggestedTools());
        assertTrue(r.getConfidence() >= 0.8);
    }

    @Test
    void llmFallback_parseFail_shouldFallbackToChitchat() {
        FixedChatModel chatModel = new FixedChatModel("我觉得是闲聊");
        IntentRouter router = new IntentRouter(chatModel);

        IntentRouter.RouteResult r = router.classify("你好呀");
        assertEquals(IntentRouter.IntentType.CHITCHAT, r.getIntentType());
        assertEquals(1, chatModel.getCalls());
    }
}

