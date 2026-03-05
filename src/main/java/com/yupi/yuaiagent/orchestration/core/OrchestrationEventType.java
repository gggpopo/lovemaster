package com.yupi.yuaiagent.orchestration.core;

/**
 * SSE v2 事件类型。
 */
public enum OrchestrationEventType {
    orchestration_started,
    intent_classified,
    policy_selected,
    agent_started,
    agent_finished,
    tool_call_started,
    tool_call_finished,
    response_chunk,
    response_completed,
    interrupted,
    failed,

    // 新增事件类型
    emotion_analyzed,
    reflection_completed,
    crisis_detected,
    memory_consolidated,
    agent_selected
}
