package com.yupi.yuaiagent.orchestration.scene;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按会话维度维护场景运行状态
 */
@Data
public class SceneRuntimeState {

    private String sceneId;

    private String sceneName;

    private SceneStage stage;

    private int turnCount;

    private long updatedAt;

    private List<String> recentUserMessages = new ArrayList<>();
}
