package com.yupi.yuaiagent.router;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRouterTest {

    static class StubChatModel implements ChatModel {

        private final String classifyText;
        private final AtomicInteger callCount = new AtomicInteger(0);

        StubChatModel(String classifyText) {
            this.classifyText = classifyText;
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(classifyText))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }
    }

    @Test
    void classify_shouldTreatFoodShopRecommendationAsDatePlanning() {
        StubChatModel chatModel = new StubChatModel("CHITCHAT|0.90");
        IntentRouter intentRouter = new IntentRouter(chatModel);

        IntentRouter.RouteResult result = intentRouter.classify("北京有什么好吃的店推荐？");

        assertEquals(IntentRouter.IntentType.DATE_PLANNING, result.getIntentType());
        assertEquals(0, chatModel.getCallCount(), "keyword route should avoid LLM fallback");
    }
}
