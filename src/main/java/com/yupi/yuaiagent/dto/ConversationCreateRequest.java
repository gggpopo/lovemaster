package com.yupi.yuaiagent.dto;

import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class ConversationCreateRequest {

    private String id;

    private String userId;

    private String title;

    private String personaId;

    private String sceneId;
}
