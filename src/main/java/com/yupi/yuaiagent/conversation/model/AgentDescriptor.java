package com.yupi.yuaiagent.conversation.model;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 注册信息。
 */
@Data
@Builder
public class AgentDescriptor {

    private String id;

    private String agentType;

    private String displayName;

    private String description;

    private Boolean enabled;
}
