package com.yupi.yuaiagent.rag;

import cn.hutool.core.util.StrUtil;

/**
 * RAG 配置归一化工具
 */
public final class RagSettingUtil {

    private RagSettingUtil() {
    }

    public static int normalizeTopK(int topK, int defaultTopK, int maxTopK) {
        if (topK <= 0) {
            return defaultTopK;
        }
        return Math.min(topK, maxTopK);
    }

    public static double normalizeSimilarityThreshold(double threshold, double defaultThreshold) {
        if (threshold < 0 || threshold > 1) {
            return defaultThreshold;
        }
        return threshold;
    }

    public static String normalizeStoreType(String storeType) {
        if (StrUtil.equalsAnyIgnoreCase(storeType, "pgvector")) {
            return "pgvector";
        }
        return "simple";
    }
}

