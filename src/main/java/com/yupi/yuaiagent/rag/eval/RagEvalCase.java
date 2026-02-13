package com.yupi.yuaiagent.rag.eval;

import java.util.List;

/**
 * 单条评测样本
 */
public record RagEvalCase(
        String caseId,
        String query,
        List<String> expectedKeywords,
        String statusFilter
) {
}

