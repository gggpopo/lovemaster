package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagSettingUtilTest {

    @Test
    void shouldNormalizeTopK() {
        assertEquals(5, RagSettingUtil.normalizeTopK(0, 5, 20));
        assertEquals(20, RagSettingUtil.normalizeTopK(99, 5, 20));
        assertEquals(8, RagSettingUtil.normalizeTopK(8, 5, 20));
    }

    @Test
    void shouldNormalizeSimilarityThreshold() {
        assertEquals(0.55d, RagSettingUtil.normalizeSimilarityThreshold(-1d, 0.55d));
        assertEquals(0.55d, RagSettingUtil.normalizeSimilarityThreshold(1.2d, 0.55d));
        assertEquals(0.8d, RagSettingUtil.normalizeSimilarityThreshold(0.8d, 0.55d));
    }

    @Test
    void shouldNormalizeStoreType() {
        assertEquals("simple", RagSettingUtil.normalizeStoreType(null));
        assertEquals("simple", RagSettingUtil.normalizeStoreType("unknown"));
        assertEquals("pgvector", RagSettingUtil.normalizeStoreType("pgvector"));
    }
}

