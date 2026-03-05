package com.yupi.yuaiagent.memory.cognitive;

import java.util.List;
import java.util.Map;

public interface CognitiveMemoryService {
    void store(String chatId, String role, String content, Map<String, Object> emotionProfile);
    List<MemoryItem> recallRecent(String chatId, int limit);
    List<MemoryItem> recallByEmotion(String chatId, String emotionType, int limit);
    List<MemoryItem> recallByImportance(String chatId, double minImportance, int limit);
    UserProfile getUserProfile(String userId);
    void updateUserProfile(String userId, UserProfile profile);
    void consolidate(String chatId);
    void decay(String chatId);
}
