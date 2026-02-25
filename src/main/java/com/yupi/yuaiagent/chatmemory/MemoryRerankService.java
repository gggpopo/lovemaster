package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记忆候选重排服务：
 * 融合语义相似度、时间新鲜度、重要度、关键词命中率进行 rerank。
 */
@Slf4j
@Service
public class MemoryRerankService {

    @Value("${app.memory.rerank.weight-similarity:0.45}")
    private double weightSimilarity = 0.45;

    @Value("${app.memory.rerank.weight-recency:0.20}")
    private double weightRecency = 0.20;

    @Value("${app.memory.rerank.weight-importance:0.20}")
    private double weightImportance = 0.20;

    @Value("${app.memory.rerank.weight-keyword:0.15}")
    private double weightKeyword = 0.15;

    public List<MemoryCandidate> rerank(String query, List<MemoryCandidate> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int safeTopK = topK <= 0 ? 5 : topK;
        long now = System.currentTimeMillis();
        List<String> tokens = splitTokens(query);
        List<MemoryCandidate> scored = new ArrayList<>();

        for (MemoryCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getContent())) {
                continue;
            }
            double similarity = clamp01(defaultValue(candidate.getSimilarity(), 0.5));
            double importance = clamp01(defaultValue(candidate.getImportance(), 0.5));
            double recency = recencyScore(defaultValue(candidate.getTimestampMs(), now), now);
            double keyword = keywordScore(tokens, candidate.getContent());

            double finalScore = similarity * weightSimilarity
                    + recency * weightRecency
                    + importance * weightImportance
                    + keyword * weightKeyword;

            candidate.setFinalScore(round(finalScore));
            Map<String, Object> metadata = candidate.getMetadata() == null ? new HashMap<>() : new HashMap<>(candidate.getMetadata());
            metadata.put("rerank_similarity", round(similarity));
            metadata.put("rerank_recency", round(recency));
            metadata.put("rerank_importance", round(importance));
            metadata.put("rerank_keyword", round(keyword));
            metadata.put("rerank_formula", "sim*ws + rec*wr + imp*wi + key*wk");
            candidate.setMetadata(metadata);
            scored.add(candidate);
        }

        scored.sort(Comparator
                .comparing((MemoryCandidate c) -> defaultValue(c.getFinalScore(), 0.0)).reversed()
                .thenComparing(c -> defaultValue(c.getTimestampMs(), 0L), Comparator.reverseOrder()));

        List<MemoryCandidate> result = scored.size() > safeTopK
                ? new ArrayList<>(scored.subList(0, safeTopK))
                : scored;

        log.info("[MemoryRerankService-rerank] queryLength={}, candidateCount={}, outputCount={}, weights={}",
                query == null ? 0 : query.length(),
                candidates.size(),
                result.size(),
                "sim=" + round(weightSimilarity) + ",rec=" + round(weightRecency)
                        + ",imp=" + round(weightImportance) + ",key=" + round(weightKeyword));
        return result;
    }

    private List<String> splitTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String[] parts = query.toLowerCase().split("[\\s,，。；;!?！？和与及]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                tokens.add(part);
            }
        }
        return new ArrayList<>(tokens);
    }

    private double keywordScore(List<String> tokens, String content) {
        if (tokens == null || tokens.isEmpty() || !StringUtils.hasText(content)) {
            return 0.0;
        }
        String lowered = content.toLowerCase();
        int hit = 0;
        for (String token : tokens) {
            if (lowered.contains(token)) {
                hit++;
            }
        }
        return clamp01((double) hit / tokens.size());
    }

    private double recencyScore(long timestampMs, long nowMs) {
        long diff = Math.max(0L, nowMs - timestampMs);
        double day = 1000D * 60 * 60 * 24;
        double days = diff / day;
        // 30 天后显著衰减，仍保留少量分值
        return clamp01(1.0 / (1.0 + days / 7.0));
    }

    private double clamp01(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    private double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private double defaultValue(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private long defaultValue(Long value, long fallback) {
        return value == null ? fallback : value;
    }
}
