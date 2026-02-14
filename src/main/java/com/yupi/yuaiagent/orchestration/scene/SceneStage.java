package com.yupi.yuaiagent.orchestration.scene;

/**
 * 场景化对话阶段
 */
public enum SceneStage {
    /**
     * 事实收集
     */
    DISCOVERY,
    /**
     * 目标对齐
     */
    GOAL_ALIGN,
    /**
     * 行动方案
     */
    ACTION_PLAN,
    /**
     * 复盘与迭代
     */
    REVIEW
}
