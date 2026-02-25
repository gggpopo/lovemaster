package com.yupi.yuaiagent.chatmemory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 记忆检索候选项（统一向量记忆 / 结构化记忆的返回模型）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCandidate {

    /**
     * 来源：vector / structured
     */
    private String source;

    /**
     * 记忆类型：conversation / preference / constraint / event / emotion 等
     */
    private String memoryType;

    /**
     * 记忆文本
     */
    private String content;

    /**
     * 原始语义相似度（0~1）
     */
    private Double similarity;

    /**
     * 重要度（0~1）
     */
    private Double importance;

    /**
     * 时间戳（毫秒）
     */
    private Long timestampMs;

    /**
     * rerank 后分数（0~1）
     */
    private Double finalScore;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
}
