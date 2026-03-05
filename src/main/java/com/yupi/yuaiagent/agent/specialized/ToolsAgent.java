package com.yupi.yuaiagent.agent.specialized;

import com.yupi.yuaiagent.orchestration.core.AgentContext;
import com.yupi.yuaiagent.orchestration.core.AgentOutput;
import com.yupi.yuaiagent.orchestration.core.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 工具调用统一管理 Agent（V3 动态编排）。
 * 将所有工具调用能力封装为一个 SubAgent，由 PlanAgent 决定何时调用。
 */
@Slf4j
@AgentCapability(
    type = AgentType.TOOLS,
    description = "工具调用 Agent，拥有的能力：[约会地点推荐, 天气查询, 网页搜索, 图片生成, 记忆召回, 日历查询, 情绪检测]",
    supportedIntents = {"DATE_PLANNING", "GIFT_ADVICE", "IMAGE_REQUEST", "WEATHER_QUERY", "WEB_SEARCH"},
    estimatedTokens = 200,
    inputSchema = "{\"toolName\": \"string\", \"params\": \"object\"}",
    outputSchema = "{\"success\": \"boolean\", \"toolName\": \"string\", \"toolResult\": \"string\", \"error\": \"string\"}"
)
@Component
public class ToolsAgent implements SpecializedAgent {

    @Resource
    private ToolCallback[] allTools;

    @Override
    public AgentType getAgentType() {
        return AgentType.TOOLS;
    }

    @Override
    public AgentOutput execute(AgentContext context) {
        if (context == null || context.getSharedState() == null) {
            log.warn("[ToolsAgent-execute] Context or sharedState is null");
            return buildErrorOutput("context_missing", "Context or sharedState is null");
        }

        Map<String, Object> sharedState = context.getSharedState();
        String toolName = (String) sharedState.get("toolName");
        Object paramsObj = sharedState.get("params");

        log.info("[ToolsAgent-execute] {}", kv("toolName", toolName, "params", paramsObj));

        if (toolName == null || toolName.isBlank()) {
            return buildErrorOutput("tool_name_missing", "toolName is required in inputContext");
        }

        // 执行工具 - 简化版本，直接返回工具名称和参数
        // 实际的工具调用由 AssetAgent 或 NarrativeAgent 处理
        try {
            log.info("[ToolsAgent-execute] Tool execution requested: {}", kv("toolName", toolName));

            return AgentOutput.builder()
                .agentType(getAgentType())
                .blocked(false)
                .summary("工具调用请求已记录: " + toolName)
                .data(Map.of(
                    "success", true,
                    "toolName", toolName,
                    "params", paramsObj == null ? Map.of() : paramsObj,
                    "toolResult", "工具调用将由后续 Agent 执行"
                ))
                .build();
        } catch (Exception e) {
            log.error("[ToolsAgent-execute] Tool execution failed: {}", kv("toolName", toolName), e);
            return buildErrorOutput("tool_execution_failed", "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * 列出所有可用工具（供 PlanAgent 查询）
     */
    public List<String> listAvailableTools() {
        if (allTools == null) {
            return List.of();
        }
        // 简化版本：返回工具数量
        return List.of("共 " + allTools.length + " 个工具可用");
    }

    /**
     * 获取工具描述（供 PlanAgent 生成 System Prompt）
     */
    public Map<String, String> getToolDescriptions() {
        if (allTools == null) {
            return Map.of();
        }
        // 简化版本：返回工具数量
        return Map.of("toolCount", String.valueOf(allTools.length));
    }

    private AgentOutput buildErrorOutput(String errorCode, String errorMessage) {
        return AgentOutput.builder()
            .agentType(getAgentType())
            .blocked(false)
            .summary("工具调用失败: " + errorCode)
            .data(Map.of(
                "success", false,
                "error", errorCode,
                "errorMessage", errorMessage
            ))
            .build();
    }
}
