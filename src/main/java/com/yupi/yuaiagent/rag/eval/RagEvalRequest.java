package com.yupi.yuaiagent.rag.eval;

import java.util.List;

/**
 * RAG 评测请求
 */
public record RagEvalRequest(
        List<RagEvalCase> cases,
        Integer topK,
        Double similarityThreshold,
        String matchMode
) {
}

