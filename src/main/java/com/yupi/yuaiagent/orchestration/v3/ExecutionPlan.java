package com.yupi.yuaiagent.orchestration.v3;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import com.yupi.yuaiagent.orchestration.model.ExecutionMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 执行计划（V3 动态编排）。
 * 由 PlanAgent 生成，描述需要调用哪些 SubAgent 及其执行顺序。
 */
@Data
@Builder
public class ExecutionPlan {

    /**
     * 用户意图分类
     * 示例：CHITCHAT, EMOTION_SUPPORT, DATE_PLANNING, GIFT_ADVICE
     */
    private String intent;

    /**
     * 需要执行的 Agent 列表（按 priority 排序）
     */
    private List<AgentTask> requiredAgents;

    /**
     * 跳过的 Agent 列表
     */
    private List<AgentType> skipAgents;

    /**
     * 执行模式（串行/并行/混合）
     */
    private ExecutionMode executionMode;

    /**
     * 预估 Token 消耗
     */
    private int estimatedTokens;

    /**
     * PlanAgent 的决策理由（用于调试和可观测性）
     */
    private String reasoning;
}
