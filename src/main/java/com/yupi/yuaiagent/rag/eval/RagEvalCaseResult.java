package com.yupi.yuaiagent.rag.eval;

import java.util.List;

/**
 * 单条评测样本结果
 */
public record RagEvalCaseResult(
        String caseId,
        String query,
        boolean hit,
        boolean empty,
        long latencyMs,
        int retrievedCount,
        String matchedKeywords,
        List<String> retrievedPreview
) {
}

