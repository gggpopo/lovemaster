package com.yupi.yuaiagent.orchestration.v2;

import com.yupi.yuaiagent.agent.specialized.AgentRegistry;
import com.yupi.yuaiagent.agent.specialized.SpecializedAgent;
import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.v2.intent.ExtendedIntentType;
import com.yupi.yuaiagent.orchestration.v2.intent.IntentResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

class AgentSelectorTest {

    private AgentSelector selector;
    private StubAgentRegistry registry;

    static class StubAgent implements SpecializedAgent {
        private final AgentType type;
        StubAgent(AgentType type) { this.type = type; }
        @Override public AgentType getAgentType() { return type; }
        @Override public AgentOutput execute(AgentContext context) {
            return AgentOutput.builder().agentType(type).blocked(false).summary("stub").data(Map.of()).build();
        }
    }

    static class StubAgentRegistry extends AgentRegistry {
        private final Map<AgentType, SpecializedAgent> agents = new EnumMap<>(AgentType.class);
        void register(AgentType type, SpecializedAgent agent) { agents.put(type, agent); }
        @Override public Optional<SpecializedAgent> getAgent(AgentType type) {
            return Optional.ofNullable(agents.get(type));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        selector = new AgentSelector();
        registry = new StubAgentRegistry();

        // Register common agents
        registry.register(AgentType.SAFETY, new StubAgent(AgentType.SAFETY));
        registry.register(AgentType.COMPANION, new StubAgent(AgentType.COMPANION));
        registry.register(AgentType.NARRATIVE, new StubAgent(AgentType.NARRATIVE));
        registry.register(AgentType.DATE_PLANNER, new StubAgent(AgentType.DATE_PLANNER));
        registry.register(AgentType.CONFLICT_MEDIATOR, new StubAgent(AgentType.CONFLICT_MEDIATOR));

        // Inject stub registry via reflection
        Field registryField = AgentSelector.class.getDeclaredField("agentRegistry");
        registryField.setAccessible(true);
        registryField.set(selector, registry);
    }

    private IntentResult buildIntent(ExtendedIntentType type) {
        return IntentResult.builder()
                .primaryIntent(null)
                .extendedIntent(type)
                .confidence(0.9)
                .suggestedTools(Set.of())
                .modelProfile("standard")
                .temperature(0.5f)
                .emotionAware(false)
                .highRisk(false)
                .build();
    }

    @Test
    void select_crisisDetected_shouldReturnSafety() {
        Map<String, Object> emotion = Map.of("crisisDetected", true);
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.CHITCHAT), emotion);
        Assertions.assertEquals(AgentType.SAFETY, agent.getAgentType());
    }

    @Test
    void select_unsafeIntent_shouldReturnSafety() {
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.UNSAFE), null);
        Assertions.assertEquals(AgentType.SAFETY, agent.getAgentType());
    }

    @Test
    void select_highNegativeEmotion_shouldReturnCompanion() {
        Map<String, Object> emotion = Map.of(
                "crisisDetected", false,
                "overallValence", -0.7,
                "maxIntensity", 8.0
        );
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.CHITCHAT), emotion);
        Assertions.assertEquals(AgentType.COMPANION, agent.getAgentType());
    }

    @Test
    void select_datePlanning_shouldReturnDatePlanner() {
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.DATE_PLANNING), null);
        Assertions.assertEquals(AgentType.DATE_PLANNER, agent.getAgentType());
    }

    @Test
    void select_conflictResolution_shouldReturnConflictMediator() {
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.CONFLICT_RESOLUTION), null);
        Assertions.assertEquals(AgentType.CONFLICT_MEDIATOR, agent.getAgentType());
    }

    @Test
    void select_unknownAgent_shouldFallbackToNarrative() {
        // IMAGE_REQUEST maps to ASSET, which is not registered
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.IMAGE_REQUEST), null);
        Assertions.assertEquals(AgentType.NARRATIVE, agent.getAgentType());
    }

    @Test
    void select_lowNegativeEmotion_shouldNotTriggerCompanion() {
        // valence = -0.3 (not < -0.5), should NOT trigger emotion priority
        Map<String, Object> emotion = Map.of(
                "crisisDetected", false,
                "overallValence", -0.3,
                "maxIntensity", 5.0
        );
        SpecializedAgent agent = selector.select(buildIntent(ExtendedIntentType.DATE_PLANNING), emotion);
        Assertions.assertEquals(AgentType.DATE_PLANNER, agent.getAgentType());
    }
}
