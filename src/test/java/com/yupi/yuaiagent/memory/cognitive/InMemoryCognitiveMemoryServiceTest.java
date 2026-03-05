package com.yupi.yuaiagent.memory.cognitive;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

class InMemoryCognitiveMemoryServiceTest {

    private InMemoryCognitiveMemoryService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryCognitiveMemoryService();
    }

    @Test
    void store_shouldCreateMemoryWithEmotionProfile() {
        Map<String, Object> emotionProfile = Map.of(
                "emotions", List.of(Map.of("type", "SAD", "intensity", 8, "weight", 1.0))
        );
        service.store("chat1", "user", "我很难过", emotionProfile);

        List<MemoryItem> items = service.recallRecent("chat1", 10);
        Assertions.assertEquals(1, items.size());
        Assertions.assertEquals("SAD", items.get(0).getEmotionType());
        Assertions.assertEquals(8.0, items.get(0).getEmotionIntensity());
        // importance = min(0.3 + 8 * 0.07, 1.0) = 0.86
        Assertions.assertTrue(items.get(0).getImportance() > 0.8);
    }

    @Test
    void store_shouldUseDefaultsWhenNoEmotionProfile() {
        service.store("chat1", "user", "你好", null);

        List<MemoryItem> items = service.recallRecent("chat1", 10);
        Assertions.assertEquals(1, items.size());
        Assertions.assertEquals("CALM", items.get(0).getEmotionType());
        Assertions.assertEquals(0.5, items.get(0).getImportance());
    }

    @Test
    void recallRecent_shouldReturnInReverseChronologicalOrder() {
        service.store("chat1", "user", "第一条", null);
        service.store("chat1", "user", "第二条", null);
        service.store("chat1", "user", "第三条", null);

        List<MemoryItem> items = service.recallRecent("chat1", 2);
        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals("第三条", items.get(0).getContent());
        Assertions.assertEquals("第二条", items.get(1).getContent());
    }

    @Test
    void recallByEmotion_shouldFilterByType() {
        service.store("chat1", "user", "开心", Map.of(
                "emotions", List.of(Map.of("type", "HAPPY", "intensity", 6, "weight", 1.0))));
        service.store("chat1", "user", "难过", Map.of(
                "emotions", List.of(Map.of("type", "SAD", "intensity", 7, "weight", 1.0))));
        service.store("chat1", "user", "也开心", Map.of(
                "emotions", List.of(Map.of("type", "HAPPY", "intensity", 5, "weight", 1.0))));

        List<MemoryItem> happy = service.recallByEmotion("chat1", "HAPPY", 10);
        Assertions.assertEquals(2, happy.size());
        happy.forEach(m -> Assertions.assertEquals("HAPPY", m.getEmotionType()));
    }

    @Test
    void recallByImportance_shouldFilterByMinImportance() {
        // intensity 2 -> importance = 0.3 + 2*0.07 = 0.44
        service.store("chat1", "user", "低重要", Map.of(
                "emotions", List.of(Map.of("type", "CALM", "intensity", 2, "weight", 1.0))));
        // intensity 9 -> importance = 0.3 + 9*0.07 = 0.93
        service.store("chat1", "user", "高重要", Map.of(
                "emotions", List.of(Map.of("type", "SAD", "intensity", 9, "weight", 1.0))));

        List<MemoryItem> important = service.recallByImportance("chat1", 0.8, 10);
        Assertions.assertEquals(1, important.size());
        Assertions.assertEquals("高重要", important.get(0).getContent());
    }

    @Test
    void consolidate_shouldBoostHighImportanceItems() {
        service.store("chat1", "user", "重要记忆", Map.of(
                "emotions", List.of(Map.of("type", "SAD", "intensity", 9, "weight", 1.0))));
        double before = service.recallRecent("chat1", 1).get(0).getImportance();

        service.consolidate("chat1");

        double after = service.recallRecent("chat1", 1).get(0).getImportance();
        Assertions.assertTrue(after > before, "Consolidation should boost importance");
        Assertions.assertTrue(after <= 1.0, "Importance should not exceed 1.0");
    }

    @Test
    void getUserProfile_shouldReturnDefaultWhenNotSet() {
        UserProfile profile = service.getUserProfile("user1");
        Assertions.assertEquals("user1", profile.getUserId());
        Assertions.assertNotNull(profile.getPreferences());
    }

    @Test
    void updateUserProfile_shouldPersist() {
        UserProfile profile = UserProfile.builder()
                .userId("user1")
                .nickname("小明")
                .relationshipStatus("恋爱中")
                .lastUpdated(Instant.now())
                .build();
        service.updateUserProfile("user1", profile);

        UserProfile loaded = service.getUserProfile("user1");
        Assertions.assertEquals("小明", loaded.getNickname());
        Assertions.assertEquals("恋爱中", loaded.getRelationshipStatus());
    }

    @Test
    void recallRecent_shouldReturnEmptyForUnknownChat() {
        List<MemoryItem> items = service.recallRecent("unknown", 10);
        Assertions.assertTrue(items.isEmpty());
    }
}
