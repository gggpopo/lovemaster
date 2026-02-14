package com.yupi.yuaiagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 编排聊天请求
 */
@Data
public class OrchestrationChatRequest {

    /**
     * 用户输入
     */
    private String message;

    /**
     * 会话 ID
     */
    private String chatId;

    /**
     * Base64 图片列表（可选）
     */
    private List<String> images;

    /**
     * 强制执行模式（可选）：chat/tool/agent/vision
     */
    private String forceMode;

    /**
     * 场景 ID（可选），如 first_date_planning / cold_war_repair / breakup_recovery
     */
    private String sceneId;
}
