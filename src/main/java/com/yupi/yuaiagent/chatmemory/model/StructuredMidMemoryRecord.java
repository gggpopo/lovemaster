package com.yupi.yuaiagent.chatmemory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化中期记忆记录模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredMidMemoryRecord {

    private String conversationId;

    private String memoryType;

    private String content;

    private Double importance;

    private Long timestampMs;

    private Map<String, Object> metadata;
}
