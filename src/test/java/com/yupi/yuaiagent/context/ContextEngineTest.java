package com.yupi.yuaiagent.context;

import com.yupi.yuaiagent.memory.cognitive.CognitiveMemoryService;
import com.yupi.yuaiagent.memory.cognitive.MemoryItem;
import com.yupi.yuaiagent.memory.cognitive.UserProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

class ContextEngineTest {

    private ContextEngine engine;
    private StubCognitiveMemoryService memoryService;

    static class StubCognitiveMemoryService implements CognitiveMemoryService {
        List<MemoryItem> recentItems = new ArrayList<>();
        List<MemoryItem> importantItems = new ArrayList<>();
        UserProfile profile;

        @Override public void store(String chatId, String role, String content, Map<String, Object> ep) {}
        @Override public List<MemoryItem> recallRecent(String chatId, int limit) {
            return recentItems.stream().limit(limit).toList();
        }
        @Override public List<MemoryItem> recallByEmotion(String chatId, String emotionType, int limit) {
            return List.of();
        }
        @Override public List<MemoryItem> recallByImportance(String chatId, double min, int limit) {
            return importantItems.stream().filter(m -> m.getImportance() >= min).limit(limit).toList();
        }
        @Override public UserProfile getUserProfile(String userId) {
            return profile != null ? profile : UserProfile.builder()
                    .userId(userId).preferences(new ArrayList<>())
                    .avoidTopics(new ArrayList<>()).customFields(new HashMap<>())
                    .lastUpdated(Instant.now()).build();
        }
        @Override public void updateUserProfile(String userId, UserProfile p) {}
        @Override public void consolidate(String chatId) {}
        @Override public void decay(String chatId) {}
    }

    @BeforeEach
    void setUp() throws Exception {
        engine = new ContextEngine();
        memoryService = new StubCognitiveMemoryService();

        // Inject stub via reflection
        Field field = ContextEngine.class.getDeclaredField("cognitiveMemoryService");
        field.setAccessible(true);
        field.set(engine, memoryService);
    }

    private MemoryItem makeItem(String id, String content, double importance, long minutesAgo) {
        return MemoryItem.builder()
                .id(id)
                .chatId("chat1")
                .role("user")
                .content(content)
                .timestamp(Instant.now().minus(minutesAgo, java.time.temporal.ChronoUnit.MINUTES))
                .importance(importance)
                .emotionType("CALM")
                .emotionIntensity(3.0)
                .metadata(Map.of())
                .build();
    }

    @Test
    void build_shouldReturnContextPacketWithMemories() {
        memoryService.recentItems = List.of(
                makeItem("m1", "你好", 0.5, 5),
                makeItem("m2", "我很开心", 0.8, 10)
        );
        memoryService.importantItems = List.of(
                makeItem("m2", "我很开心", 0.8, 10) // duplicate
        );

        ContextPacket packet = engine.build("chat1", "user1", "测试",
                Map.of("narrativeSummary", "用户情绪平稳"));

        Assertions.assertNotNull(packet);
        Assertions.assertTrue(packet.getEstimatedTokens() > 0);
        Assertions.assertEquals("用户情绪平稳", packet.getEmotionSummary());
        // Deduplication: m2 appears in both lists but should only appear once
        Assertions.assertTrue(packet.getRelevantMemories().size() <= 2);
    }

    @Test
    void build_shouldFilterLowScoreMemories() {
        // Very old item with low importance -> score should be < 0.2
        memoryService.recentItems = List.of(
                makeItem("m1", "很久以前的事", 0.1, 60 * 24 * 10) // 10 days ago, importance 0.1
        );

        ContextPacket packet = engine.build("chat1", "user1", "测试", null);

        // Low score items should be filtered out
        Assertions.assertEquals("情绪状态未知", packet.getEmotionSummary());
    }

    @Test
    void build_shouldIncludeProfileSummary() {
        memoryService.profile = UserProfile.builder()
                .userId("user1")
                .nickname("小红")
                .relationshipStatus("恋爱中")
                .preferences(List.of("看电影", "吃火锅"))
                .avoidTopics(new ArrayList<>())
                .customFields(new HashMap<>())
                .lastUpdated(Instant.now())
                .build();

        ContextPacket packet = engine.build("chat1", "user1", "测试", null);

        Assertions.assertTrue(packet.getUserProfileSummary().contains("小红"));
        Assertions.assertTrue(packet.getUserProfileSummary().contains("恋爱中"));
        Assertions.assertTrue(packet.getUserProfileSummary().contains("看电影"));
    }

    @Test
    void build_shouldRespectTokenBudget() {
        // Create many large memories that exceed token budget
        List<MemoryItem> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(makeItem("m" + i, "这是一段很长的记忆内容".repeat(20), 0.9, i));
        }
        memoryService.recentItems = items;

        ContextPacket packet = engine.build("chat1", "user1", "测试", null);

        // Should not include all 50 items due to token budget
        Assertions.assertTrue(packet.getRelevantMemories().size() < 50);
        Assertions.assertTrue(packet.getEstimatedTokens() <= 2000);
    }

    @Test
    void build_emptyMemories_shouldReturnValidPacket() {
        ContextPacket packet = engine.build("chat1", "user1", "测试", null);

        Assertions.assertNotNull(packet);
        Assertions.assertTrue(packet.getRelevantMemories().isEmpty());
    }
}
