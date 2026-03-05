package com.yupi.yuaiagent.tool.registry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AnnotationToolRegistry {

    @Resource
    private ApplicationContext applicationContext;

    // toolName -> ToolEntry
    private final Map<String, ToolEntry> registry = new ConcurrentHashMap<>();

    public record ToolEntry(
            String name,
            String description,
            ToolDomain[] domains,
            ToolSafetyLevel safety,
            String[] requiredAgents,
            ToolCallback[] callbacks
    ) {}

    @PostConstruct
    public void init() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(EmotionalTool.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            EmotionalTool annotation = bean.getClass().getAnnotation(EmotionalTool.class);
            if (annotation == null) continue;

            try {
                ToolCallback[] callbacks = ToolCallbacks.from(bean);
                ToolEntry toolEntry = new ToolEntry(
                        annotation.name(), annotation.description(),
                        annotation.domains(), annotation.safety(),
                        annotation.requiredAgents(), callbacks
                );
                registry.put(annotation.name(), toolEntry);
                log.info("[AnnotationToolRegistry-init] registered tool, name={}, domains={}, safety={}",
                        annotation.name(), Arrays.toString(annotation.domains()), annotation.safety());
            } catch (Exception e) {
                log.warn("[AnnotationToolRegistry-init] failed to register tool, bean={}", entry.getKey(), e);
            }
        }
        log.info("[AnnotationToolRegistry-init] total tools registered, count={}", registry.size());
    }

    public List<ToolCallback> getToolsForDomain(ToolDomain domain) {
        return registry.values().stream()
                .filter(e -> Arrays.asList(e.domains()).contains(domain))
                .flatMap(e -> Arrays.stream(e.callbacks()))
                .toList();
    }

    public List<ToolCallback> getToolsForAgent(String agentName) {
        return registry.values().stream()
                .filter(e -> e.requiredAgents().length == 0
                        || Arrays.asList(e.requiredAgents()).contains(agentName))
                .filter(e -> e.safety() != ToolSafetyLevel.DANGEROUS)
                .flatMap(e -> Arrays.stream(e.callbacks()))
                .toList();
    }

    public List<ToolCallback> getSafeTools() {
        return registry.values().stream()
                .filter(e -> e.safety() == ToolSafetyLevel.SAFE)
                .flatMap(e -> Arrays.stream(e.callbacks()))
                .toList();
    }

    public Optional<ToolEntry> getTool(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public Collection<ToolEntry> getAllTools() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
