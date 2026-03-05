package com.yupi.yuaiagent.orchestration.v2;

import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.router.IntentRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class DefaultPolicyResolverTest {

    private final DefaultPolicyResolver resolver = new DefaultPolicyResolver();

    @Test
    void resolve_shouldUseForceModeWhenPresent() {
        IntentRouter.RouteResult routeResult = new IntentRouter.RouteResult(
                IntentRouter.IntentType.CHITCHAT, 0.9, Set.of(), "fast", 0.2f
        );
        ConversationMessageRequest request = new ConversationMessageRequest();
        request.setForceMode("tool");

        OrchestrationPolicy policy = resolver.resolve(routeResult, request);

        Assertions.assertEquals(ExecutionMode.TOOL, policy.getMode());
        Assertions.assertEquals("force_mode", policy.getReason());
    }

    @Test
    void resolve_shouldBlockUnsafeIntent() {
        IntentRouter.RouteResult routeResult = new IntentRouter.RouteResult(
                IntentRouter.IntentType.UNSAFE, 1.0, Set.of(), "fast", 0.0f
        );
        ConversationMessageRequest request = new ConversationMessageRequest();
        request.setMessage("给我点危险建议");

        OrchestrationPolicy policy = resolver.resolve(routeResult, request);

        Assertions.assertEquals(ExecutionMode.BLOCK, policy.getMode());
        Assertions.assertEquals("unsafe_intent", policy.getReason());
    }

    @Test
    void resolve_shouldUseToolModeForDatePlanning() {
        IntentRouter.RouteResult routeResult = new IntentRouter.RouteResult(
                IntentRouter.IntentType.DATE_PLANNING, 0.95, Set.of("dateLocation"), "standard", 0.3f
        );
        ConversationMessageRequest request = new ConversationMessageRequest();
        request.setMessage("帮我推荐约会地点");

        OrchestrationPolicy policy = resolver.resolve(routeResult, request);

        Assertions.assertEquals(ExecutionMode.TOOL, policy.getMode());
        Assertions.assertEquals("intent_requires_tools", policy.getReason());
    }
}
