package com.yupi.yuaiagent.conversation.model;

import lombok.Builder;
import lombok.Data;

/**
 * 对话消息实体。
 */
@Data
@Builder
public class MessageEntity {

    private String id;

    private String conversationId;

    private String role;

    private String content;

    private String imagesJson;

    private String metadataJson;

    private Long createdAt;
}
