package com.yupi.yuaiagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 会话消息请求体。
 */
@Data
public class ConversationMessageRequest {

    /**
     * 用户消息
     */
    private String message;

    /**
     * 图片（Base64）
     */
    private List<String> images;

    /**
     * 强制执行模式：chat/tool/agent/vision/block
     */
    private String forceMode;

    /**
     * 场景 ID
     */
    private String sceneId;

    /**
     * 人格 ID
     */
    private String personaId;

    /**
     * 用户 ID（可选）
     */
    private String userId;
}
