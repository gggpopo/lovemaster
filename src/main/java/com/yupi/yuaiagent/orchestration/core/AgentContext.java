package com.yupi.yuaiagent.orchestration.core;

import com.yupi.yuaiagent.orchestration.model.OrchestrationPolicy;
import com.yupi.yuaiagent.router.IntentRouter;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行上下文。
 */
@Data
@Builder
public class AgentContext {

    private String conversationId;

    private String userId;

    private String personaId;

    private String sceneId;

    private String message;

    private List<String> images;

    private IntentRouter.RouteResult routeResult;

    private OrchestrationPolicy policy;

    private Map<String, Object> sharedState;

    private CancellationToken cancellationToken;

    private Map<String, Object> emotionProfile;
}
