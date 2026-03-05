package com.yupi.yuaiagent.orchestration.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent 执行结果。
 */
@Data
@Builder
public class AgentOutput {

    private AgentType agentType;

    private boolean blocked;

    private String summary;

    private Map<String, Object> data;
}
