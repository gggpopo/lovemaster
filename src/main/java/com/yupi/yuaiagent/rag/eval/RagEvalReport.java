package com.yupi.yuaiagent.rag.eval;

import java.util.List;

/**
 * RAG 评测报告
 */
public record RagEvalReport(
        int topK,
        double similarityThreshold,
        String matchMode,
        int totalCases,
        int hitCount,
        int missCount,
        int emptyCount,
        double hitAtK,
        double emptyRate,
        long p95LatencyMs,
        long avgLatencyMs,
        long durationMs,
        List<RagEvalCaseResult> caseResults
) {
}

