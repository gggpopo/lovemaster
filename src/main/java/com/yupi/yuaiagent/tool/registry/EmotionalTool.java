package com.yupi.yuaiagent.tool.registry;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EmotionalTool {
    String name();
    String description();
    ToolDomain[] domains() default {ToolDomain.GENERAL};
    ToolSafetyLevel safety() default ToolSafetyLevel.SAFE;
    String[] requiredAgents() default {};
}
