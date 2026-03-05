package com.yupi.yuaiagent.orchestration.core;

/**
 * 专业 Agent 类型。
 */
public enum AgentType {
    // 现有类型
    SAFETY,
    EMOTION,
    PERSONA,
    MEMORY,
    NARRATIVE,
    ASSET,

    // 新增类型 — 情感助手专业 Agent
    EMOTION_ANALYST,      // 深度情绪分析（替代简单的 EMOTION）
    COMPANION,            // 情感陪伴
    DATE_PLANNER,         // 约会策划
    CONFLICT_MEDIATOR,    // 冲突调解
    REFLECTION,           // 回复质量自检
    MEMORY_CURATOR,       // 记忆管理

    // V3 动态编排新增类型
    TOOLS,                // 工具调用统一管理 Agent
    PLAN                  // 执行计划生成 Agent
}
