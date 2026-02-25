package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合记忆召回服务：结构化中期记忆 + 向量长期记忆 + rerank。
 */
@Slf4j
@Service
public class HybridMemoryRecallService implements MemoryRecallFacade {

    private final StructuredMidMemoryService structuredMidMemoryService;
    private final VectorMemoryService vectorMemoryService;
    private final MemoryRerankService memoryRerankService;

    @Value("${app.memory.recall.candidate-limit:20}")
    private int candidateLimit = 20;

    public HybridMemoryRecallService(StructuredMidMemoryService structuredMidMemoryService,
                                     VectorMemoryService vectorMemoryService,
                                     MemoryRerankService memoryRerankService) {
        this.structuredMidMemoryService = structuredMidMemoryService;
        this.vectorMemoryService = vectorMemoryService;
        this.memoryRerankService = memoryRerankService;
    }

    @Override
    public List<MemoryCandidate> recall(String conversationId, String query, int topK) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(query)) {
            return List.of();
        }
        int safeTopK = topK <= 0 ? 5 : topK;
        int safeCandidateLimit = Math.max(safeTopK, candidateLimit);

        long startMs = System.currentTimeMillis();
        List<MemoryCandidate> mergedCandidates = new ArrayList<>();

        List<MemoryCandidate> structuredCandidates = structuredMidMemoryService.search(conversationId, query, safeCandidateLimit);
        if (structuredCandidates != null && !structuredCandidates.isEmpty()) {
            mergedCandidates.addAll(structuredCandidates);
        }

        List<MemoryCandidate> vectorCandidates = vectorMemoryService.searchCandidates(conversationId, query, safeCandidateLimit);
        if (vectorCandidates != null && !vectorCandidates.isEmpty()) {
            mergedCandidates.addAll(vectorCandidates);
        }

        if (mergedCandidates.isEmpty()) {
            return List.of();
        }
        List<MemoryCandidate> ranked = memoryRerankService.rerank(query, mergedCandidates, safeTopK);

        log.info("[HybridMemoryRecallService-recall] conversationId={}, queryLength={}, structuredCount={}, vectorCount={}, mergedCount={}, rankedCount={}, costMs={}",
                conversationId,
                query.length(),
                structuredCandidates == null ? 0 : structuredCandidates.size(),
                vectorCandidates == null ? 0 : vectorCandidates.size(),
                mergedCandidates.size(),
                ranked.size(),
                System.currentTimeMillis() - startMs);
        return ranked;
    }
}
