package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryRerankServiceTest {

    @Test
    void rerank_shouldPrioritizeRecentImportantAndKeywordMatchedCandidate() {
        MemoryRerankService service = new MemoryRerankService();
        long now = System.currentTimeMillis();

        MemoryCandidate oldHighSimilarity = MemoryCandidate.builder()
                .source("vector")
                .memoryType("conversation")
                .content("去年提到预算5000")
                .similarity(0.95)
                .importance(0.2)
                .timestampMs(now - 1000L * 60 * 60 * 24 * 120)
                .metadata(Map.of())
                .build();

        MemoryCandidate recentKeywordMatch = MemoryCandidate.builder()
                .source("structured")
                .memoryType("constraint")
                .content("用户当前约会预算是300，偏好安静咖啡馆")
                .similarity(0.65)
                .importance(0.9)
                .timestampMs(now - 1000L * 60 * 10)
                .metadata(Map.of())
                .build();

        List<MemoryCandidate> ranked = service.rerank("预算300 咖啡馆", List.of(oldHighSimilarity, recentKeywordMatch), 2);
        assertEquals("用户当前约会预算是300，偏好安静咖啡馆", ranked.get(0).getContent());
    }
}
