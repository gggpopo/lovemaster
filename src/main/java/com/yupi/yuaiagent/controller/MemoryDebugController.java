package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.chatmemory.ConversationSummaryService;
import com.yupi.yuaiagent.chatmemory.TieredChatMemoryAdvisor;
import com.yupi.yuaiagent.chatmemory.VectorMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 三层记忆调试接口：查看 window/summary/vector 数据，并提供可写入的 seed 对话。
 *
 * 默认关闭，需设置：app.debug.memory.enabled=true
 */
@Slf4j
@RestController
@RequestMapping("/debug/memory")
@ConditionalOnProperty(name = "app.debug.memory.enabled", havingValue = "true")
public class MemoryDebugController {

    private final ChatMemory chatMemory;
    private final ConversationSummaryService summaryService;
    private final VectorMemoryService vectorMemoryService;
    private final TieredChatMemoryAdvisor tieredChatMemoryAdvisor;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${app.memory.vector.pgvector.table-name:conversation_memory_store}")
    private String pgvectorTableName;

    public MemoryDebugController(ChatMemory chatMemory,
                                 ConversationSummaryService summaryService,
                                 VectorMemoryService vectorMemoryService,
                                 ObjectProvider<TieredChatMemoryAdvisor> tieredChatMemoryAdvisorProvider,
                                 ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                 ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.chatMemory = chatMemory;
        this.summaryService = summaryService;
        this.vectorMemoryService = vectorMemoryService;
        this.tieredChatMemoryAdvisor = tieredChatMemoryAdvisorProvider.getIfAvailable();
        this.stringRedisTemplateProvider = stringRedisTemplateProvider;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /**
     * 查看滑动窗口层的数据（实际来源可能是 file / redis / in-memory 的 ChatMemoryRepository）。
     */
    @GetMapping("/window")
    public List<Map<String, Object>> window(@RequestParam String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages) {
            if (m == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", m.getMessageType() == null ? null : m.getMessageType().name());
            row.put("text", m.getText());
            out.add(row);
        }
        return out;
    }

    /**
     * 查看摘要层的数据。
     *
     * - summaryService.getSummary：优先从 Redis 读（app.memory.redis.enabled=true 且连接可用）
     * - redisRaw：可选，直接读 Redis key 方便对照
     */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam String conversationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("summary", summaryService.getSummary(conversationId));

        StringRedisTemplate redis = stringRedisTemplateProvider.getIfAvailable();
        if (redis != null) {
            String key = "love_master:summary:" + conversationId;
            try {
                out.put("redisKey", key);
                out.put("redisRaw", redis.opsForValue().get(key));
            } catch (Exception e) {
                out.put("redisError", e.getMessage());
            }
        }
        return out;
    }

    /**
     * 通过 VectorStore 做语义检索，查看“长期记忆召回”的结果。
     */
    @GetMapping("/vector/search")
    public Map<String, Object> vectorSearch(@RequestParam String conversationId,
                                            @RequestParam String q,
                                            @RequestParam(defaultValue = "5") int topK) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("query", q);
        out.put("topK", topK);
        out.put("result", vectorMemoryService.retrieveRelevantMemories(conversationId, q, topK));
        return out;
    }

    /**
     * 直接从 PgVector 表里看原始数据（仅在你开启 datasource + PgVectorStore 时有效）。
     *
     * 注意：不同版本 PgVectorStore 的表结构可能略有差异；这里做 best-effort。
     */
    @GetMapping("/vector/raw")
    public Map<String, Object> vectorRaw(@RequestParam String conversationId,
                                         @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("table", pgvectorTableName);

        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            out.put("supported", false);
            out.put("reason", "JdbcTemplate 不存在（通常表示未启用 datasource / PgVector）");
            return out;
        }

        try {
            // PgVectorStore 默认 metadata 为 jsonb，这里用 ->> 取字符串
            String sql = "select id, content, metadata from " + pgvectorTableName +
                    " where metadata->>'conversation_id' = ? order by id desc limit ?";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, conversationId, limit);
            out.put("supported", true);
            out.put("rows", rows);
            return out;
        } catch (Exception e) {
            out.put("supported", false);
            out.put("error", e.getMessage());
            return out;
        }
    }

    /**
     * 生成一段“可写入的对话”，用于把三层记忆的数据打进去（不依赖真实 LLM 对话）。
     *
     * 会通过 TieredChatMemoryAdvisor 走完整链路：
     * - before：注入 summary / long-term
     * - after：写 window、触发淘汰后更新 summary、写 vector long-term
     */
    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestBody SeedRequest req) throws Exception {
        String conversationId = StringUtils.hasText(req.conversationId()) ? req.conversationId() : UUID.randomUUID().toString();
        int turns = req.turns() == null ? 12 : Math.max(1, req.turns());
        int waitMs = req.waitMs() == null ? 400 : Math.max(0, req.waitMs());
        String userPrefix = StringUtils.hasText(req.userPrefix()) ? req.userPrefix() : "用户:";
        String assistantPrefix = StringUtils.hasText(req.assistantPrefix()) ? req.assistantPrefix() : "助手:";

        if (tieredChatMemoryAdvisor == null) {
            return Map.of(
                    "ok", false,
                    "message", "TieredChatMemoryAdvisor 未装配（请确认 ChatMemoryConfig 生效）"
            );
        }

        AtomicInteger seq = new AtomicInteger(0);
        CallAdvisorChain chain = new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                int n = seq.incrementAndGet();
                String assistantText = assistantPrefix + " 第" + n + "轮：收到，给出建议与总结（用于写入记忆）";
                ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
                return new ChatClientResponse(chatResponse, request.context());
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }
        };

        for (int i = 1; i <= turns; i++) {
            // 用更“像真实恋爱对话”的内容，便于向量召回（偏好/地点/纪念日等关键词）
            String userText = userPrefix + " 第" + i + "轮：我喜欢猫，想去西湖附近约会，预算 300，纪念日是 2024-02-14";
            Prompt prompt = new Prompt(List.of(new SystemMessage("SEED"), new UserMessage(userText)));
            Map<String, Object> ctx = new HashMap<>();
            ctx.put(ChatMemory.CONVERSATION_ID, conversationId);
            tieredChatMemoryAdvisor.adviseCall(new ChatClientRequest(prompt, ctx), chain);
        }

        if (waitMs > 0) {
            TimeUnit.MILLISECONDS.sleep(waitMs);
        }

        String q = StringUtils.hasText(req.query()) ? req.query() : "西湖 约会 预算 纪念日";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("conversationId", conversationId);
        out.put("turns", turns);
        out.put("window", window(conversationId));
        out.put("summary", summary(conversationId));
        out.put("vectorSearch", vectorSearch(conversationId, q, req.topK() == null ? 5 : req.topK()));
        return out;
    }

    public record SeedRequest(
            String conversationId,
            Integer turns,
            Integer waitMs,
            String userPrefix,
            String assistantPrefix,
            String query,
            Integer topK
    ) {
    }
}

