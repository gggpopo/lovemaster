package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标注专业 Agent 的能力元数据，用于自动注册和发现。
 * V3 增强：支持动态编排所需的输入输出 Schema 和 Token 预估。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AgentCapability {
    AgentType type();
    String description() default "";
    String[] supportedIntents() default {};
    int priority() default 0;  // higher = preferred
    boolean supportsStreaming() default false;

    /**
     * Agent 的输入 Schema（JSON 格式描述）
     * 示例：{"message": "string"} 或 {"conversationId": "string", "query": "string"}
     */
    String inputSchema() default "{}";

    /**
     * Agent 的输出 Schema（JSON 格式描述）
     * 示例：{"safe": "boolean", "code": "string"}
     */
    String outputSchema() default "{}";

    /**
     * 预估 Token 消耗（用于 PlanAgent 决策）
     */
    int estimatedTokens() default 100;

    /**
     * 是否为必需 Agent（如 SafetyAgent 必须执行）
     */
    boolean required() default false;
}
