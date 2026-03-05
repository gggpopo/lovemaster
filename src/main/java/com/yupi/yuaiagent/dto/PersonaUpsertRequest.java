package com.yupi.yuaiagent.dto;

import lombok.Data;

/**
 * 人格新增/更新请求。
 */
@Data
public class PersonaUpsertRequest {

    private String name;

    private String description;

    private String systemPrompt;

    private String styleGuideJson;

    private Boolean enabled;
}
