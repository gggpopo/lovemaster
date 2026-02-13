package com.yupi.yuaiagent.orchestration.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 编排策略结果
 */
@Value
@Builder
public class OrchestrationPolicy {

    ExecutionMode mode;

    String reason;

    String modelProfile;

    float temperature;

    Set<String> suggestedTools;
}
