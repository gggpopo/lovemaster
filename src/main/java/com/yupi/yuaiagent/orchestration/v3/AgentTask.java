package com.yupi.yuaiagent.orchestration.v3;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent 任务定义（V3 动态编排）。
 * 每个任务包含独立的输入上下文，实现 Agent 间的上下文隔离。
 */
@Data
@Builder
public class AgentTask {

    /**
     * Agent 类型
     */
    private AgentType agentType;

    /**
     * 执行优先级（数字越小越先执行）
     */
    private int priority;

    /**
     * 调用该 Agent 的理由（由 PlanAgent 生成）
     */
    private String reason;

    /**
     * 执行模式
     */
    private ExecutionMode executionMode;

    /**
     * 该 Agent 的独立输入上下文（关键设计：实现上下文隔离）
     *
     * 示例：
     * - SafetyAgent: {"message": "用户消息"}
     * - EmotionAgent: {"message": "用户消息"}
     * - MemoryAgent: {"conversationId": "conv_123", "query": "吵架"}
     * - PersonaAgent: {"personaId": "gentle_advisor"}
     * - ToolsAgent: {"toolName": "searchDateLocations", "params": {"city": "北京"}}
     */
    private Map<String, Object> inputContext;
}
