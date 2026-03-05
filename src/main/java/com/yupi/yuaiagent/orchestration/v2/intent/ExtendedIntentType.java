package com.yupi.yuaiagent.orchestration.v2.intent;

/**
 * 扩展意图类型，覆盖情感场景细分。
 */
public enum ExtendedIntentType {
    // 日常
    CHITCHAT,
    EMOTION_SUPPORT,

    // 约会相关
    DATE_PLANNING,
    GIFT_ADVICE,

    // 创意
    LOVE_COPYWRITING,

    // 知识
    RELATIONSHIP_QA,

    // 图片
    IMAGE_REQUEST,

    // 安全
    UNSAFE,

    // 新增：冲突相关
    CONFLICT_RESOLUTION,   // 一般冲突
    COLD_WAR_REPAIR,       // 冷战修复
    BREAKUP_RECOVERY,      // 分手挽回

    // 新增：主动关怀
    PROACTIVE_CARE         // 主动关怀触发
}
