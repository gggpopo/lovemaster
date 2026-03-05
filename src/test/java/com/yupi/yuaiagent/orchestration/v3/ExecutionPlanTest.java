package com.yupi.yuaiagent.orchestration.v3;

import com.yupi.yuaiagent.orchestration.core.AgentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V3 动态编排数据模型测试
 */
class ExecutionPlanTest {

    @Test
    void testExecutionPlanBuilder() {
        // 测试 ExecutionPlan 构建
        AgentTask task1 = AgentTask.builder()
                .agentType(AgentType.SAFETY)
                .priority(1)
                .reason("必须检查输入安全性")
                .inputContext(Map.of("message", "你好"))
                .build();

        AgentTask task2 = AgentTask.builder()
                .agentType(AgentType.EMOTION)
                .priority(2)
                .reason("识别用户情绪")
                .inputContext(Map.of("message", "你好"))
                .build();

        ExecutionPlan plan = ExecutionPlan.builder()
                .intent("CHITCHAT")
                .requiredAgents(List.of(task1, task2))
                .skipAgents(List.of(AgentType.MEMORY, AgentType.TOOLS))
                .estimatedTokens(130)
                .reasoning("简单闲聊场景")
                .build();

        // 验证
        assertNotNull(plan);
        assertEquals("CHITCHAT", plan.getIntent());
        assertEquals(2, plan.getRequiredAgents().size());
        assertEquals(2, plan.getSkipAgents().size());
        assertEquals(130, plan.getEstimatedTokens());

        // 验证 AgentTask
        AgentTask firstTask = plan.getRequiredAgents().get(0);
        assertEquals(AgentType.SAFETY, firstTask.getAgentType());
        assertEquals(1, firstTask.getPriority());
        assertEquals("你好", firstTask.getInputContext().get("message"));
    }

    @Test
    void testAgentTaskInputContext() {
        // 测试 inputContext 的上下文隔离
        Map<String, Object> safetyContext = Map.of("message", "用户消息");
        Map<String, Object> memoryContext = Map.of(
                "conversationId", "conv_123",
                "query", "吵架"
        );
        Map<String, Object> toolsContext = Map.of(
                "toolName", "searchDateLocations",
                "params", Map.of("city", "北京")
        );

        AgentTask safetyTask = AgentTask.builder()
                .agentType(AgentType.SAFETY)
                .inputContext(safetyContext)
                .build();

        AgentTask memoryTask = AgentTask.builder()
                .agentType(AgentType.MEMORY)
                .inputContext(memoryContext)
                .build();

        AgentTask toolsTask = AgentTask.builder()
                .agentType(AgentType.TOOLS)
                .inputContext(toolsContext)
                .build();

        // 验证每个 Agent 的 inputContext 是独立的
        assertNotEquals(safetyTask.getInputContext(), memoryTask.getInputContext());
        assertNotEquals(memoryTask.getInputContext(), toolsTask.getInputContext());

        // 验证 inputContext 内容
        assertEquals("用户消息", safetyTask.getInputContext().get("message"));
        assertEquals("conv_123", memoryTask.getInputContext().get("conversationId"));
        assertEquals("searchDateLocations", toolsTask.getInputContext().get("toolName"));
    }

    @Test
    void testExecutionPlanWithEmotionSupport() {
        // 测试情感支持场景的执行计划
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent("EMOTION_SUPPORT")
                .requiredAgents(List.of(
                        AgentTask.builder()
                                .agentType(AgentType.SAFETY)
                                .priority(1)
                                .inputContext(Map.of("message", "我很焦虑"))
                                .build(),
                        AgentTask.builder()
                                .agentType(AgentType.EMOTION)
                                .priority(2)
                                .inputContext(Map.of("message", "我很焦虑"))
                                .build(),
                        AgentTask.builder()
                                .agentType(AgentType.MEMORY)
                                .priority(3)
                                .inputContext(Map.of("conversationId", "conv_123", "query", "焦虑"))
                                .build()
                ))
                .skipAgents(List.of(AgentType.TOOLS))
                .estimatedTokens(280)
                .reasoning("情感支持场景，需要情绪识别和记忆召回")
                .build();

        assertNotNull(plan);
        assertEquals("EMOTION_SUPPORT", plan.getIntent());
        assertEquals(3, plan.getRequiredAgents().size());
        assertEquals(1, plan.getSkipAgents().size());
        assertTrue(plan.getSkipAgents().contains(AgentType.TOOLS));
    }
}
