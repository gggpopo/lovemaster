package com.yupi.yuaiagent.orchestration.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredResponseComposerTest {

    @Test
    void compose_shouldBuildLocationCardAndTextBlocks() {
        StructuredResponseComposer composer = new StructuredResponseComposer(new ObjectMapper());
        String raw = "<!--LOCATION_CARD:{\"name\":\"聚宝源\",\"address\":\"牛街5号\",\"photos\":[\"/api/proxy/image?url=a\"],\"mapUrl\":\"https://uri.amap.com/marker\"}-->\n"
                + "为你找到了口碑不错的店，建议提前排号。";

        AssistantResponseSchema response = composer.compose(
                "love_test",
                "DATE_PLANNING",
                "TOOL",
                raw,
                0.92,
                false
        );

        assertNotNull(response);
        assertEquals("assistant_response_v2", response.getSchemaVersion());
        assertEquals("DATE_PLANNING", response.getIntent());
        assertEquals("TOOL", response.getMode());
        assertNotNull(response.getBlocks());
        assertTrue(response.getBlocks().stream().anyMatch(block -> "location_cards".equals(block.getType())));
        assertTrue(response.getBlocks().stream().anyMatch(block -> "text".equals(block.getType())));

        ResponseBlock locationBlock = response.getBlocks().stream()
                .filter(block -> "location_cards".equals(block.getType()))
                .findFirst()
                .orElseThrow();
        Object itemsObj = locationBlock.getData().get("items");
        assertTrue(itemsObj instanceof List<?>);
        List<?> items = (List<?>) itemsObj;
        assertFalse(items.isEmpty());
        assertTrue(items.get(0) instanceof Map<?, ?>);
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertEquals("聚宝源", String.valueOf(first.get("name")));
    }

    @Test
    void compose_shouldBuildRiskAlertWhenBlocked() {
        StructuredResponseComposer composer = new StructuredResponseComposer(new ObjectMapper());
        AssistantResponseSchema response = composer.compose(
                "love_test",
                "UNSAFE",
                "BLOCK",
                "该请求有潜在风险，无法继续协助。",
                1.0,
                true
        );

        assertNotNull(response);
        assertEquals("warning", response.getSafety().getLevel());
        assertTrue(response.getBlocks().stream().anyMatch(block -> "risk_alert".equals(block.getType())));
    }
}
