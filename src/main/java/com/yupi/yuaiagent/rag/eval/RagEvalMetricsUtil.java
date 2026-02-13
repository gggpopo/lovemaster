package com.yupi.yuaiagent.rag.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG 评测指标计算工具
 */
public final class RagEvalMetricsUtil {

    private RagEvalMetricsUtil() {
    }

    public static long p95(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.95d) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public static double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return (double) numerator / denominator;
    }
}

