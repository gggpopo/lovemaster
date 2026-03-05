package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import reactor.core.publisher.Flux;

/**
 * 支持流式输出的专业 Agent。
 */
public interface StreamableAgent extends SpecializedAgent {
    Flux<String> executeStream(AgentContext context);
}
