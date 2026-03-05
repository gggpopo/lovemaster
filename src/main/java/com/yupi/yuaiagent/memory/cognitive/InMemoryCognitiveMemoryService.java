package com.yupi.yuaiagent.memory.cognitive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InMemoryCognitiveMemoryService implements CognitiveMemoryService {

    // chatId -> list of memories
    private final Map<String, List<MemoryItem>> memoryStore = new ConcurrentHashMap<>();
    // userId -> profile
    private final Map<String, UserProfile> profileStore = new ConcurrentHashMap<>();

    private static final int MAX_ITEMS_PER_CHAT = 200;
    private static final double CONSOLIDATION_THRESHOLD = 0.7;

    @Override
    public void store(String chatId, String role, String content, Map<String, Object> emotionProfile) {
        String emotionType = "CALM";
        double emotionIntensity = 3.0;
        double importance = 0.5;

        if (emotionProfile != null) {
            Object emotions = emotionProfile.get("emotions");
            if (emotions instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object typeVal = m.get("type");
                    emotionType = typeVal != null ? String.valueOf(typeVal) : "CALM";
                    Object intensity = m.get("intensity");
                    if (intensity instanceof Number n) emotionIntensity = n.doubleValue();
                }
            }
            // Higher emotion intensity = higher importance
            importance = Math.min(0.3 + emotionIntensity * 0.07, 1.0);
        }

        MemoryItem item = MemoryItem.builder()
                .id(UUID.randomUUID().toString())
                .chatId(chatId)
                .role(role)
                .content(content)
                .timestamp(Instant.now())
                .importance(importance)
                .emotionType(emotionType)
                .emotionIntensity(emotionIntensity)
                .metadata(emotionProfile != null ? new HashMap<>(emotionProfile) : Map.of())
                .build();

        memoryStore.computeIfAbsent(chatId, k -> Collections.synchronizedList(new ArrayList<>())).add(item);

        // Evict oldest low-importance items if over capacity
        List<MemoryItem> items = memoryStore.get(chatId);
        if (items.size() > MAX_ITEMS_PER_CHAT) {
            items.sort(Comparator.comparingDouble(MemoryItem::getImportance));
            while (items.size() > MAX_ITEMS_PER_CHAT) {
                items.remove(0);
            }
        }

        log.info("[CognitiveMemory-store] chatId={}, role={}, emotionType={}, importance={}",
                chatId, role, emotionType, importance);
    }

    @Override
    public List<MemoryItem> recallRecent(String chatId, int limit) {
        List<MemoryItem> items = memoryStore.getOrDefault(chatId, List.of());
        return items.stream()
                .sorted(Comparator.comparing(MemoryItem::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<MemoryItem> recallByEmotion(String chatId, String emotionType, int limit) {
        List<MemoryItem> items = memoryStore.getOrDefault(chatId, List.of());
        return items.stream()
                .filter(m -> emotionType.equalsIgnoreCase(m.getEmotionType()))
                .sorted(Comparator.comparing(MemoryItem::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<MemoryItem> recallByImportance(String chatId, double minImportance, int limit) {
        List<MemoryItem> items = memoryStore.getOrDefault(chatId, List.of());
        return items.stream()
                .filter(m -> m.getImportance() >= minImportance)
                .sorted(Comparator.comparingDouble(MemoryItem::getImportance).reversed())
                .limit(limit)
                .toList();
    }


    @Override
    public UserProfile getUserProfile(String userId) {
        return profileStore.getOrDefault(userId, UserProfile.builder()
                .userId(userId)
                .preferences(new ArrayList<>())
                .avoidTopics(new ArrayList<>())
                .customFields(new HashMap<>())
                .lastUpdated(Instant.now())
                .build());
    }

    @Override
    public void updateUserProfile(String userId, UserProfile profile) {
        profile.setLastUpdated(Instant.now());
        profileStore.put(userId, profile);
        log.info("[CognitiveMemory-updateProfile] userId={}", userId);
    }

    @Override
    public void consolidate(String chatId) {
        List<MemoryItem> items = memoryStore.getOrDefault(chatId, List.of());
        long promoted = items.stream()
                .filter(m -> m.getImportance() >= CONSOLIDATION_THRESHOLD)
                .peek(m -> m.setImportance(Math.min(m.getImportance() + 0.1, 1.0)))
                .count();
        log.info("[CognitiveMemory-consolidate] chatId={}, totalItems={}, promoted={}", chatId, items.size(), promoted);
    }

    @Override
    public void decay(String chatId) {
        List<MemoryItem> items = memoryStore.getOrDefault(chatId, List.of());
        Instant now = Instant.now();
        int decayed = 0;
        Iterator<MemoryItem> it = items.iterator();
        while (it.hasNext()) {
            MemoryItem m = it.next();
            long ageDays = Duration.between(m.getTimestamp(), now).toDays();
            double decayFactor = Math.max(Math.exp(-0.1 * ageDays), 0.1);
            double newImportance = m.getImportance() * decayFactor;
            if (newImportance < 0.1) {
                it.remove();
                decayed++;
            } else {
                m.setImportance(newImportance);
            }
        }
        log.info("[CognitiveMemory-decay] chatId={}, decayed={}, remaining={}", chatId, decayed, items.size());
    }
}
