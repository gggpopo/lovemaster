package com.yupi.yuaiagent.rag.eval;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.rag.RagSettingUtil;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RAG 评测引擎（纯计算，不依赖 Spring）
 */
public class RagEvaluationEngine {

    @FunctionalInterface
    public interface Retriever {
        List<Document> retrieve(String query, int topK, double similarityThreshold, String statusFilter);
    }

    public RagEvalReport evaluate(Retriever retriever,
                                  RagEvalRequest request,
                                  int defaultTopK,
                                  int maxTopK,
                                  double defaultSimilarityThreshold) {
        long start = System.currentTimeMillis();
        List<RagEvalCase> evalCases = request == null || request.cases() == null ? List.of() : request.cases();
        int topK = RagSettingUtil.normalizeTopK(request == null || request.topK() == null ? defaultTopK : request.topK(), defaultTopK, maxTopK);
        double similarityThreshold = RagSettingUtil.normalizeSimilarityThreshold(
                request == null || request.similarityThreshold() == null ? defaultSimilarityThreshold : request.similarityThreshold(),
                defaultSimilarityThreshold
        );
        MatchMode matchMode = MatchMode.of(request == null ? null : request.matchMode());

        List<RagEvalCaseResult> caseResults = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        int hitCount = 0;
        int missCount = 0;
        int emptyCount = 0;

        for (RagEvalCase evalCase : evalCases) {
            String query = evalCase == null ? "" : StrUtil.blankToDefault(evalCase.query(), "");
            String caseId = evalCase == null ? "" : StrUtil.blankToDefault(evalCase.caseId(), "");
            List<Document> retrievedDocs = new ArrayList<>();

            long caseStart = System.currentTimeMillis();
            if (StrUtil.isNotBlank(query)) {
                List<Document> docs = retriever.retrieve(query, topK, similarityThreshold, evalCase == null ? null : evalCase.statusFilter());
                if (docs != null) {
                    retrievedDocs = docs;
                }
            }
            long latency = System.currentTimeMillis() - caseStart;
            latencies.add(latency);

            boolean empty = retrievedDocs.isEmpty();
            if (empty) {
                emptyCount++;
            }

            HitResult hitResult = evaluateHit(retrievedDocs, evalCase == null ? null : evalCase.expectedKeywords(), matchMode);
            boolean hit = hitResult.hit();
            if (hit) {
                hitCount++;
            } else {
                missCount++;
            }

            caseResults.add(new RagEvalCaseResult(
                    caseId,
                    query,
                    hit,
                    empty,
                    latency,
                    retrievedDocs.size(),
                    hitResult.matchedKeywords(),
                    buildPreview(retrievedDocs, 3, 120)
            ));
        }

        int totalCases = evalCases.size();
        return new RagEvalReport(
                topK,
                similarityThreshold,
                matchMode.name(),
                totalCases,
                hitCount,
                missCount,
                emptyCount,
                RagEvalMetricsUtil.rate(hitCount, totalCases),
                RagEvalMetricsUtil.rate(emptyCount, totalCases),
                RagEvalMetricsUtil.p95(latencies),
                average(latencies),
                System.currentTimeMillis() - start,
                caseResults
        );
    }

    private HitResult evaluateHit(List<Document> docs, List<String> expectedKeywords, MatchMode matchMode) {
        List<String> keywords = expectedKeywords == null ? List.of() : expectedKeywords.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            return new HitResult(docs != null && !docs.isEmpty(), "");
        }
        if (docs == null || docs.isEmpty()) {
            return new HitResult(false, "");
        }
        String joinedText = docs.stream()
                .filter(Objects::nonNull)
                .map(Document::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));

        List<String> matched = keywords.stream()
                .filter(keyword -> containsIgnoreCase(joinedText, keyword))
                .toList();
        boolean hit = matchMode == MatchMode.ALL
                ? matched.size() == keywords.size()
                : !matched.isEmpty();
        return new HitResult(hit, String.join("|", matched));
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        return StrUtil.containsIgnoreCase(text, keyword);
    }

    private List<String> buildPreview(List<Document> docs, int limit, int maxChars) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<String> preview = new ArrayList<>();
        for (Document doc : docs) {
            if (doc == null || StrUtil.isBlank(doc.getText())) {
                continue;
            }
            String text = doc.getText();
            preview.add(text.length() <= maxChars ? text : text.substring(0, maxChars));
            if (preview.size() >= limit) {
                break;
            }
        }
        return preview;
    }

    private long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (Long value : values) {
            sum += value == null ? 0L : value;
        }
        return sum / values.size();
    }

    private record HitResult(boolean hit, String matchedKeywords) {
    }
}

