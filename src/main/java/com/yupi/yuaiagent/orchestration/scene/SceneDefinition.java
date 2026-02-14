package com.yupi.yuaiagent.orchestration.scene;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 场景定义：每个场景拥有自己的基础提示词与阶段提示词。
 */
@Data
@Builder
public class SceneDefinition {

    /**
     * 场景 ID（如 first_date_planning）
     */
    private String sceneId;

    /**
     * 场景名称（用于日志和前端展示）
     */
    private String sceneName;

    /**
     * 场景基础 Prompt（跨阶段固定）
     */
    private String basePrompt;

    /**
     * 分阶段 Prompt
     */
    private Map<SceneStage, String> stagePrompts;

    /**
     * 场景偏好工具
     */
    private Set<String> preferredTools;
}
