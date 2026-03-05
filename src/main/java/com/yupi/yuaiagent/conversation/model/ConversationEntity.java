package com.yupi.yuaiagent.conversation.model;

import lombok.Builder;
import lombok.Data;

/**
 * 对话会话实体。
 */
@Data
@Builder
public class ConversationEntity {

    private String id;

    private String userId;

    private String personaId;

    private String title;

    private String sceneId;

    private String status;

    private Long createdAt;

    private Long updatedAt;
}
