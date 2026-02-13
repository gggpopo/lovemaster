package com.yupi.yuaiagent.rag.eval;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * RAG 评测服务
 */
@Service
@Slf4j
public class RagEvaluationService {

    private final RagEvaluationEngine engine = new RagEvaluationEngine();

    @Resource
    @Qualifier("loveAppVectorStore")
    private VectorStore loveAppVectorStore;

    @Value("${app.rag.eval.default-top-k:5}")
    private int defaultTopK;

    @Value("${app.rag.eval.max-top-k:20}")
    private int maxTopK;

    @Value("${app.rag.eval.default-similarity-threshold:0.55}")
    private double defaultSimilarityThreshold;

    public RagEvalReport evaluate(RagEvalRequest request) {
        log.info("[RagEvaluationService-evaluate] {}",
                kv("inputCaseCount", request == null || request.cases() == null ? 0 : request.cases().size(),
                        "topK", request == null ? null : request.topK(),
                        "similarityThreshold", request == null ? null : request.similarityThreshold(),
                        "matchMode", request == null ? null : request.matchMode()));

        RagEvalReport report = engine.evaluate(this::retrieve, request, defaultTopK, maxTopK, defaultSimilarityThreshold);
        log.info("[RagEvaluationService-evaluate] {}",
                kv("totalCases", report.totalCases(),
                        "hitCount", report.hitCount(),
                        "missCount", report.missCount(),
                        "emptyCount", report.emptyCount(),
                        "hitAtK", report.hitAtK(),
                        "emptyRate", report.emptyRate(),
                        "p95LatencyMs", report.p95LatencyMs(),
                        "avgLatencyMs", report.avgLatencyMs(),
                        "durationMs", report.durationMs()));
        return report;
    }

    private List<Document> retrieve(String query, int topK, double similarityThreshold, String statusFilter) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (StrUtil.isNotBlank(statusFilter)) {
            builder.filterExpression("status == '" + escape(statusFilter) + "'");
        }
        List<Document> documents = loveAppVectorStore.similaritySearch(builder.build());
        return documents == null ? List.of() : documents;
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("'", "\\'");
    }
}

