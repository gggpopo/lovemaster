package com.yupi.yuaiagent.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import com.yupi.yuaiagent.chatmemory.model.StructuredMidMemoryRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化中期记忆服务：
 * - 从对话消息中抽取偏好/约束/事件等结构化片段
 * - 写入 memory_record_v2（可选）并提供检索
 */
@Slf4j
@Service
public class StructuredMidMemoryService {

    private static final String DB_MEMORY_TABLE = "memory_record_v2";
    private static final Pattern BUDGET_PATTERN = Pattern.compile("预算\\s*([0-9]{2,6})");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[月/-]\\d{1,2}[日号]?)");
    private static final Pattern PREFERENCE_PATTERN = Pattern.compile("(喜欢|偏好|不喜欢|讨厌)[^，。,.!?！？]{1,32}");
    private static final Pattern EVENT_PATTERN = Pattern.compile("(冷战|吵架|分手|复合|纪念日|见家长|求婚|结婚)");

    private static final int DEFAULT_TOP_K = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<StructuredMidMemoryRecord>> localStore = new ConcurrentHashMap<>();

    private volatile boolean dbWriteEnabled = true;
    private volatile boolean dbReadEnabled = true;

    @Value("${app.memory.mid.enabled:true}")
    private boolean midMemoryEnabled = true;

    @Value("${app.memory.mid.max-records-per-batch:8}")
    private int maxRecordsPerBatch = 8;

    @Value("${app.memory.mid.search-scan-limit:60}")
    private int searchScanLimit = 60;

    @Value("${app.memory.mid.default-importance:0.70}")
    private double defaultImportance = 0.70;

    public StructuredMidMemoryService(@Nullable JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveFromEvictedMessages(String conversationId, List<Message> messages) {
        if (!midMemoryEnabled) {
            return;
        }
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        long startMs = System.currentTimeMillis();
        List<StructuredMidMemoryRecord> records = extractStructuredRecords(conversationId, messages);
        if (records.isEmpty()) {
            return;
        }

        boolean writtenToDb = false;
        if (jdbcTemplate != null && dbWriteEnabled) {
            writtenToDb = writeToDb(records);
        }
        if (!writtenToDb) {
            writeToLocal(records);
        }

        log.info("[StructuredMidMemoryService-save] conversationId={}, sourceSize={}, extractedSize={}, storage={}, costMs={}",
                conversationId, messages.size(), records.size(), writtenToDb ? "db" : "local", System.currentTimeMillis() - startMs);
    }

    public List<MemoryCandidate> search(String conversationId, String query, int topK) {
        if (!midMemoryEnabled) {
            return List.of();
        }
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        int safeTopK = topK <= 0 ? DEFAULT_TOP_K : topK;
        int safeScanLimit = Math.max(safeTopK, searchScanLimit);

        List<StructuredMidMemoryRecord> records = readFromDb(conversationId, safeScanLimit);
        if (records.isEmpty()) {
            records = new ArrayList<>(localStore.getOrDefault(conversationId, List.of()));
        }
        if (records.isEmpty()) {
            return List.of();
        }

        List<String> queryTokens = splitTokens(query);
        List<MemoryCandidate> candidates = new ArrayList<>();
        for (StructuredMidMemoryRecord record : records) {
            if (record == null || !StringUtils.hasText(record.getContent())) {
                continue;
            }
            double keywordScore = keywordOverlapScore(queryTokens, record.getContent());
            if (!queryTokens.isEmpty() && keywordScore <= 0) {
                continue;
            }
            MemoryCandidate candidate = MemoryCandidate.builder()
                    .source("structured")
                    .memoryType(record.getMemoryType())
                    .content(record.getContent())
                    .similarity(keywordScore)
                    .importance(record.getImportance())
                    .timestampMs(record.getTimestampMs())
                    .metadata(record.getMetadata())
                    .build();
            candidates.add(candidate);
        }

        candidates.sort(Comparator
                .comparing((MemoryCandidate c) -> safeDouble(c.getSimilarity(), 0.0)).reversed()
                .thenComparing(c -> safeLong(c.getTimestampMs(), 0L), Comparator.reverseOrder()));
        if (candidates.size() > safeTopK) {
            return new ArrayList<>(candidates.subList(0, safeTopK));
        }
        return candidates;
    }

    private List<StructuredMidMemoryRecord> extractStructuredRecords(String conversationId, List<Message> messages) {
        Set<String> unique = new LinkedHashSet<>();
        List<StructuredMidMemoryRecord> records = new ArrayList<>();
        long ts = System.currentTimeMillis();

        for (Message message : messages) {
            if (message == null || !StringUtils.hasText(message.getText())) {
                continue;
            }
            String text = normalizeText(message.getText());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            appendPatternMatches(records, unique, conversationId, text, "constraint", BUDGET_PATTERN, ts, 0.86, 4);
            appendPatternMatches(records, unique, conversationId, text, "event_date", DATE_PATTERN, ts, 0.82, 3);
            appendPatternMatches(records, unique, conversationId, text, "preference", PREFERENCE_PATTERN, ts, 0.88, 2);
            appendPatternMatches(records, unique, conversationId, text, "event", EVENT_PATTERN, ts, 0.75, 2);

            if (records.size() < maxRecordsPerBatch) {
                String summary = text.length() > 72 ? text.substring(0, 72) : text;
                appendRecord(records, unique, StructuredMidMemoryRecord.builder()
                        .conversationId(conversationId)
                        .memoryType("conversation")
                        .content(summary)
                        .importance(defaultImportance)
                        .timestampMs(ts)
                        .metadata(Map.of("source", "mid_memory_fallback"))
                        .build());
            }

            if (records.size() >= maxRecordsPerBatch) {
                break;
            }
        }
        return records;
    }

    private void appendPatternMatches(List<StructuredMidMemoryRecord> records,
                                      Set<String> unique,
                                      String conversationId,
                                      String text,
                                      String memoryType,
                                      Pattern pattern,
                                      long timestampMs,
                                      double importance,
                                      int maxMatchCount) {
        if (records.size() >= maxRecordsPerBatch) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (records.size() >= maxRecordsPerBatch || count >= maxMatchCount) {
                return;
            }
            String matched = matcher.group();
            if (!StringUtils.hasText(matched)) {
                continue;
            }
            appendRecord(records, unique, StructuredMidMemoryRecord.builder()
                    .conversationId(conversationId)
                    .memoryType(memoryType)
                    .content(matched.trim())
                    .importance(Math.max(defaultImportance, importance))
                    .timestampMs(timestampMs)
                    .metadata(Map.of("source", "regex_extract", "pattern", pattern.pattern()))
                    .build());
            count++;
        }
    }

    private void appendRecord(List<StructuredMidMemoryRecord> records,
                              Set<String> unique,
                              StructuredMidMemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getContent())) {
            return;
        }
        String key = record.getMemoryType() + "|" + record.getContent();
        if (!unique.add(key)) {
            return;
        }
        records.add(record);
    }

    private boolean writeToDb(List<StructuredMidMemoryRecord> records) {
        if (jdbcTemplate == null || records == null || records.isEmpty()) {
            return false;
        }
        String sql = "insert into " + DB_MEMORY_TABLE + " " +
                "(user_id, conversation_id, memory_type, content, importance, metadata, created_at, updated_at) " +
                "values (?, ?, ?, ?, ?, ?::jsonb, now(), now())";
        try {
            for (StructuredMidMemoryRecord record : records) {
                String metadataJson = objectMapper.writeValueAsString(record.getMetadata() == null ? Map.of() : record.getMetadata());
                String userId = record.getConversationId();
                jdbcTemplate.update(sql,
                        userId,
                        record.getConversationId(),
                        record.getMemoryType(),
                        record.getContent(),
                        safeDouble(record.getImportance(), defaultImportance),
                        metadataJson
                );
            }
            return true;
        } catch (Exception e) {
            dbWriteEnabled = false;
            log.warn("[StructuredMidMemoryService-writeToDb] fail, fallback to local, size={}", records.size(), e);
            return false;
        }
    }

    private void writeToLocal(List<StructuredMidMemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String conversationId = records.get(0).getConversationId();
        List<StructuredMidMemoryRecord> current = new ArrayList<>(localStore.getOrDefault(conversationId, List.of()));
        current.addAll(records);
        current.sort(Comparator.comparing(StructuredMidMemoryRecord::getTimestampMs, Comparator.nullsLast(Comparator.reverseOrder())));
        if (current.size() > 300) {
            current = new ArrayList<>(current.subList(0, 300));
        }
        localStore.put(conversationId, current);
    }

    private List<StructuredMidMemoryRecord> readFromDb(String conversationId, int limit) {
        if (jdbcTemplate == null || !dbReadEnabled) {
            return List.of();
        }
        String sql = "select memory_type, content, importance, created_at, metadata " +
                "from " + DB_MEMORY_TABLE + " where conversation_id = ? and is_deleted = false " +
                "order by created_at desc limit ?";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Timestamp ts = rs.getTimestamp("created_at");
                String metadataJson = rs.getString("metadata");
                Map<String, Object> metadata = parseMetadata(metadataJson);
                return StructuredMidMemoryRecord.builder()
                        .conversationId(conversationId)
                        .memoryType(rs.getString("memory_type"))
                        .content(rs.getString("content"))
                        .importance(rs.getDouble("importance"))
                        .timestampMs(ts == null ? Instant.now().toEpochMilli() : ts.getTime())
                        .metadata(metadata)
                        .build();
            }, conversationId, limit);
        } catch (Exception e) {
            dbReadEnabled = false;
            log.warn("[StructuredMidMemoryService-readFromDb] fail, fallback to local, conversationId={}, limit={}",
                    conversationId, limit, e);
            return List.of();
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", metadataJson);
            return fallback;
        }
    }

    private List<String> splitTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String[] parts = query.toLowerCase().split("[\\s,，。；;!?！？和与及]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part != null && part.length() >= 1) {
                tokens.add(part);
            }
        }
        return new ArrayList<>(tokens);
    }

    private double keywordOverlapScore(List<String> queryTokens, String content) {
        if (queryTokens == null || queryTokens.isEmpty() || !StringUtils.hasText(content)) {
            return 0.0;
        }
        String lowered = content.toLowerCase();
        int hit = 0;
        for (String token : queryTokens) {
            if (lowered.contains(token)) {
                hit++;
            }
        }
        return hit <= 0 ? 0.0 : ((double) hit / queryTokens.size());
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private double safeDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private long safeLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }
}
