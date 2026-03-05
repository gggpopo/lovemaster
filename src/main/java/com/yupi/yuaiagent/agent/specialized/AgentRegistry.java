package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * Agent 注册中心，自动发现并索引所有 SpecializedAgent。
 */
@Slf4j
@Component
public class AgentRegistry {

    @Resource
    private ApplicationContext applicationContext;

    private final Map<AgentType, SpecializedAgent> agentMap = new EnumMap<>(AgentType.class);
    private final Map<String, List<SpecializedAgent>> intentAgentMap = new HashMap<>();

    @PostConstruct
    public void init() {
        Map<String, SpecializedAgent> beans = applicationContext.getBeansOfType(SpecializedAgent.class);
        for (SpecializedAgent agent : beans.values()) {
            AgentType type = agent.getAgentType();
            agentMap.put(type, agent);

            AgentCapability capability = agent.getClass().getAnnotation(AgentCapability.class);
            if (capability != null) {
                for (String intent : capability.supportedIntents()) {
                    intentAgentMap.computeIfAbsent(intent, k -> new ArrayList<>()).add(agent);
                }
            }
            log.info("[AgentRegistry-init] registered agent, type={}, class={}", type, agent.getClass().getSimpleName());
        }
        // Sort by priority (descending)
        intentAgentMap.values().forEach(list ->
            list.sort((a, b) -> {
                int pa = getPriority(a);
                int pb = getPriority(b);
                return Integer.compare(pb, pa);
            })
        );
        log.info("[AgentRegistry-init] total agents registered, count={}", agentMap.size());
    }

    public Optional<SpecializedAgent> getAgent(AgentType type) {
        return Optional.ofNullable(agentMap.get(type));
    }

    public List<SpecializedAgent> getAgentsForIntent(String intentType) {
        return intentAgentMap.getOrDefault(intentType, List.of());
    }

    public Collection<SpecializedAgent> getAllAgents() {
        return Collections.unmodifiableCollection(agentMap.values());
    }

    private int getPriority(SpecializedAgent agent) {
        AgentCapability cap = agent.getClass().getAnnotation(AgentCapability.class);
        return cap != null ? cap.priority() : 0;
    }
}
