package com.yupi.yuaiagent.orchestration.scene;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 单轮编排所需的场景上下文
 */
@Data
@Builder
public class SceneContext {

    private String sceneId;

    private String sceneName;

    private SceneStage sceneStage;

    private int turnCount;

    private String basePrompt;

    private String stagePrompt;

    private List<String> recentUserMessages;

    private Set<String> preferredTools;
}
