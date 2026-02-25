package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.chatmemory.MemoryRecallFacade;
import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecallToolTest {

    @Test
    void recallUserMemory_shouldFormatRankedMemories() {
        MemoryRecallFacade facade = (conversationId, query, topK) -> List.of(
                MemoryCandidate.builder()
                        .source("structured")
                        .memoryType("constraint")
                        .content("预算300")
                        .finalScore(0.91)
                        .metadata(Map.of("reason", "keyword_overlap"))
                        .build(),
                MemoryCandidate.builder()
                        .source("vector")
                        .memoryType("conversation")
                        .content("喜欢西湖附近")
                        .finalScore(0.73)
                        .metadata(Map.of("reason", "semantic_similarity"))
                        .build()
        );

        MemoryRecallTool tool = new MemoryRecallTool(facade);
        String result = tool.recallUserMemory("c1", "我之前预算多少", 2);

        assertTrue(result.contains("记忆召回结果"));
        assertTrue(result.contains("预算300"));
        assertTrue(result.contains("喜欢西湖附近"));
        assertTrue(result.contains("0.91"));
    }
}
