package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;

/**
 * 专业 Agent 统一协议。
 */
public interface SpecializedAgent {

    AgentType getAgentType();

    AgentOutput execute(AgentContext context);
}
