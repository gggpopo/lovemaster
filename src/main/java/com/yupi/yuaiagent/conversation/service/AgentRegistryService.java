package com.yupi.yuaiagent.conversation.service;

import com.yupi.yuaiagent.conversation.model.AgentDescriptor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 注册信息查询服务。
 */
@Service
public class AgentRegistryService {

    public List<AgentDescriptor> listAgents() {
        return List.of(
                AgentDescriptor.builder().id("safety").agentType("SAFETY").displayName("安全Agent")
                        .description("输入输出安全审查").enabled(true).build(),
                AgentDescriptor.builder().id("emotion").agentType("EMOTION").displayName("情绪Agent")
                        .description("识别情绪标签和强度").enabled(true).build(),
                AgentDescriptor.builder().id("memory").agentType("MEMORY").displayName("记忆Agent")
                        .description("召回历史上下文").enabled(true).build(),
                AgentDescriptor.builder().id("persona").agentType("PERSONA").displayName("人格Agent")
                        .description("注入人格约束和风格").enabled(true).build(),
                AgentDescriptor.builder().id("narrative").agentType("NARRATIVE").displayName("叙事Agent")
                        .description("生成最终回复").enabled(true).build(),
                AgentDescriptor.builder().id("asset").agentType("ASSET").displayName("资产Agent")
                        .description("工具调用与资源编排").enabled(true).build()
        );
    }
}
