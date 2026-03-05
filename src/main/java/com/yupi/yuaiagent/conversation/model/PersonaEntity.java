package com.yupi.yuaiagent.conversation.model;

import lombok.Builder;
import lombok.Data;

/**
 * 人格实体。
 */
@Data
@Builder
public class PersonaEntity {

    private String id;

    private String name;

    private String description;

    private String systemPrompt;

    private String styleGuideJson;

    private Boolean enabled;

    private Long createdAt;

    private Long updatedAt;
}
