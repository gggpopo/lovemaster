package com.yupi.yuaiagent.tool.registry;

public enum ToolSafetyLevel {
    SAFE,             // 安全，任何 Agent 可用
    REQUIRES_REVIEW,  // 需要审查
    DANGEROUS         // 危险操作
}
