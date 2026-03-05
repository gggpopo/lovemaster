package com.yupi.yuaiagent.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ContextPacket {
    private String systemPrompt;
    private String userProfileSummary;
    private String emotionSummary;
    private List<String> relevantMemories;
    private Map<String, Object> metadata;
    private int estimatedTokens;
}
