package com.yupi.yuaiagent.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvalMetricsUtilTest {

    @Test
    void shouldCalculateP95Latency() {
        long p95 = RagEvalMetricsUtil.p95(List.of(10L, 20L, 30L, 40L, 100L));
        assertEquals(100L, p95);
    }

    @Test
    void shouldCalculateRate() {
        assertEquals(0.4d, RagEvalMetricsUtil.rate(2, 5));
        assertEquals(0d, RagEvalMetricsUtil.rate(2, 0));
    }
}

