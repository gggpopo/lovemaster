package com.yupi.yuaiagent.rag.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvaluationEngineTest {

    @Test
    void shouldEvaluateHitRateAndEmptyRateWithAnyMode() {
        RagEvaluationEngine engine = new RagEvaluationEngine();
        RagEvalRequest request = new RagEvalRequest(
                List.of(
                        new RagEvalCase("c1", "西湖约会", List.of("西湖"), null),
                        new RagEvalCase("c2", "送礼预算", List.of("预算"), null)
                ),
                5,
                0.55d,
                "ANY"
        );

        RagEvalReport report = engine.evaluate((query, topK, threshold, statusFilter) -> {
            if ("西湖约会".equals(query)) {
                return List.of(new Document("推荐你去西湖边散步"));
            }
            return List.of();
        }, request, 5, 20, 0.55d);

        assertEquals(2, report.totalCases());
        assertEquals(1, report.hitCount());
        assertEquals(1, report.emptyCount());
        assertEquals(0.5d, report.hitAtK());
        assertEquals(0.5d, report.emptyRate());
    }

    @Test
    void shouldSupportAllMatchMode() {
        RagEvaluationEngine engine = new RagEvaluationEngine();
        RagEvalRequest request = new RagEvalRequest(
                List.of(new RagEvalCase("c1", "冲突沟通", List.of("倾听", "复述"), null)),
                5,
                0.55d,
                "ALL"
        );

        RagEvalReport report = engine.evaluate((query, topK, threshold, statusFilter) ->
                        List.of(new Document("建议先倾听再提需求")),
                request, 5, 20, 0.55d);

        assertEquals(0, report.hitCount());
        assertEquals(1, report.missCount());
    }
}

