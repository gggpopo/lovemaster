package com.yupi.yuaiagent.orchestration.v2.intent;

import com.yupi.yuaiagent.router.IntentRouter;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 增强的意图分析结果。
 */
@Data
@Builder
public class IntentResult {
    /** 原始意图类型（来自 IntentRouter） */
    private IntentRouter.IntentType primaryIntent;

    /** 扩展意图类型（情感场景细分） */
    private ExtendedIntentType extendedIntent;

    /** 置信度 0.0-1.0 */
    private double confidence;

    /** 建议使用的工具 */
    private Set<String> suggestedTools;

    /** 模型配置 */
    private String modelProfile;

    /** 温度参数 */
    private float temperature;

    /** 是否需要情绪感知 */
    private boolean emotionAware;

    /** 是否高风险场景（冲突/分手等） */
    private boolean highRisk;
}
