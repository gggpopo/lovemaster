package com.yupi.yuaiagent.orchestration.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将模型文本输出归一化为结构化响应协议。
 */
@Slf4j
@Component
public class StructuredResponseComposer {

    private static final String SCHEMA_VERSION = "assistant_response_v2";
    private static final Pattern LOCATION_CARD_PATTERN = Pattern.compile("<!--LOCATION_CARD:(.*?)-->", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public StructuredResponseComposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssistantResponseSchema compose(String chatId,
                                           String intent,
                                           String mode,
                                           String rawText,
                                           Double confidence,
                                           boolean blocked) {
        long startMs = System.currentTimeMillis();
        String normalizedText = rawText == null ? "" : rawText.trim();

        List<ResponseBlock> blocks = new ArrayList<>();
        List<Map<String, Object>> locations = extractLocationCards(normalizedText);
        String textWithoutLocationCards = LOCATION_CARD_PATTERN.matcher(normalizedText).replaceAll("").trim();

        if (!locations.isEmpty()) {
            blocks.add(ResponseBlock.builder()
                    .type("location_cards")
                    .id(blockId("location_cards"))
                    .title("地点推荐")
                    .data(Map.of("items", locations))
                    .build());
        }

        if (blocked) {
            blocks.add(ResponseBlock.builder()
                    .type("risk_alert")
                    .id(blockId("risk_alert"))
                    .title("安全提示")
                    .data(Map.of(
                            "level", "high",
                            "message", StringUtils.hasText(textWithoutLocationCards)
                                    ? textWithoutLocationCards
                                    : "该请求存在潜在风险，我不能继续提供相关协助。"))
                    .build());
        } else if (StringUtils.hasText(textWithoutLocationCards)) {
            blocks.add(ResponseBlock.builder()
                    .type("text")
                    .id(blockId("text"))
                    .title("建议")
                    .data(Map.of("text", cleanupReadableText(textWithoutLocationCards)))
                    .build());
        }

        if (blocks.isEmpty()) {
            blocks.add(ResponseBlock.builder()
                    .type("text")
                    .id(blockId("text"))
                    .title("建议")
                    .data(Map.of("text", "暂时没有可展示的内容，请换个问法我继续帮你。"))
                    .build());
        }

        AssistantResponseSchema response = AssistantResponseSchema.builder()
                .schemaVersion(SCHEMA_VERSION)
                .responseId("resp_" + UUID.randomUUID().toString().substring(0, 8))
                .chatId(chatId)
                .intent(normalizeUpper(intent))
                .mode(normalizeUpper(mode))
                .summary(extractSummary(blocks))
                .safety(SafetyMeta.builder()
                        .level(blocked ? "warning" : "safe")
                        .flags(blocked ? List.of("unsafe_intent") : List.of())
                        .build())
                .confidence(ConfidenceMeta.builder()
                        .overall(confidence == null ? 0.0 : confidence)
                        .build())
                .blocks(blocks)
                .followUp(buildFollowUp(blocks, blocked))
                .createdAt(System.currentTimeMillis())
                .build();

        log.info("[StructuredResponseComposer-compose] chatId={}, intent={}, mode={}, blockCount={}, hasLocationCards={}, costMs={}",
                chatId, response.getIntent(), response.getMode(), blocks.size(), !locations.isEmpty(),
                System.currentTimeMillis() - startMs);
        return response;
    }

    private List<Map<String, Object>> extractLocationCards(String text) {
        List<Map<String, Object>> cards = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return cards;
        }

        Matcher matcher = LOCATION_CARD_PATTERN.matcher(text);
        while (matcher.find()) {
            String json = matcher.group(1);
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                Map<String, Object> card = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
                cards.add(normalizeLocationCard(card));
            } catch (Exception e) {
                log.warn("[StructuredResponseComposer-extractLocationCards] parse failed", e);
            }
        }
        return cards;
    }

    private Map<String, Object> normalizeLocationCard(Map<String, Object> raw) {
        Map<String, Object> card = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        // 统一字段：images 供前端 schema 渲染使用，photos 兼容 LocationCard 组件。
        Object photos = card.get("photos");
        List<String> imageList = new ArrayList<>();
        if (photos instanceof List<?>) {
            for (Object item : (List<?>) photos) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isEmpty()) {
                    imageList.add(value);
                }
            }
        }
        card.put("images", imageList);
        card.put("photos", imageList);
        return card;
    }

    private FollowUpMeta buildFollowUp(List<ResponseBlock> blocks, boolean blocked) {
        if (blocked) {
            return FollowUpMeta.builder()
                    .question("你可以换一个安全、合法的目标，我继续帮你。")
                    .choices(List.of("换个问题", "先聊聊背景", "需要情绪支持"))
                    .build();
        }
        boolean hasLocation = blocks.stream().anyMatch(block -> "location_cards".equals(block.getType()));
        if (hasLocation) {
            return FollowUpMeta.builder()
                    .question("你更想要哪种类型地点？")
                    .choices(List.of("正餐", "咖啡", "景点"))
                    .build();
        }
        return FollowUpMeta.builder()
                .question("你希望我下一步更侧重哪部分？")
                .choices(List.of("具体行动", "沟通话术", "风险分析"))
                .build();
    }

    private String extractSummary(List<ResponseBlock> blocks) {
        for (ResponseBlock block : blocks) {
            if (!"text".equals(block.getType()) || block.getData() == null) {
                continue;
            }
            String text = String.valueOf(block.getData().getOrDefault("text", "")).trim();
            if (text.isEmpty()) {
                continue;
            }
            String[] lines = text.split("\\n");
            for (String line : lines) {
                String candidate = line.trim();
                if (!candidate.isEmpty()) {
                    return candidate.length() > 80 ? candidate.substring(0, 80) + "..." : candidate;
                }
            }
        }
        return "已为你整理结构化建议。";
    }

    private String cleanupReadableText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String out = text
                .replaceAll("(?m)^\\s*\\|\\s*$", "")
                .replaceAll("(?m)^\\s*📸[^\\n]*$", "")
                .trim();
        return out;
    }

    private String blockId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}
