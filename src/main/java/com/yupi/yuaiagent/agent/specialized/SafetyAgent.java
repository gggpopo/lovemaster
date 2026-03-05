package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 输入安全审查 Agent。
 */
@AgentCapability(
    type = AgentType.SAFETY,
    description = "检测输入安全性，拦截高风险内容（暴力、色情、违法等）",
    supportedIntents = {"*"},  // 所有意图都需要安全检查
    required = true,
    estimatedTokens = 50,
    inputSchema = "{\"message\": \"string\"}",
    outputSchema = "{\"safe\": \"boolean\", \"code\": \"string\", \"level\": \"string\"}"
)
@Component
public class SafetyAgent implements SpecializedAgent {

    private static final Pattern UNSAFE_PATTERN = Pattern.compile(
            "(自杀|爆炸|炸弹|毒品|枪|走私|诈骗|恐怖袭击|极端主义|约炮|黄片|色情)"
    );

    @Override
    public AgentType getAgentType() {
        return AgentType.SAFETY;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        String message = context == null ? "" : String.valueOf(context.getMessage() == null ? "" : context.getMessage());
        boolean blocked = UNSAFE_PATTERN.matcher(message).find();
        if (blocked) {
            return AgentOutput.builder()
                    .agentType(getAgentType())
                    .blocked(true)
                    .summary("输入包含高风险内容，已拦截")
                    .data(Map.of(
                            "code", "unsafe_input",
                            "level", "high"
                    ))
                    .build();
        }
        return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("输入安全")
                .data(Map.of(
                        "code", "safe",
                        "level", "low"
                ))
                .build();
    }
}
