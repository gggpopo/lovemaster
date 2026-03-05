package com.yupi.yuaiagent.context;

import com.yupi.yuaiagent.memory.cognitive.CognitiveMemoryService;
import com.yupi.yuaiagent.memory.cognitive.MemoryItem;
import com.yupi.yuaiagent.memory.cognitive.UserProfile;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * GSSC 上下文管道：Gather → Select → Structure → Compress
 */
@Slf4j
@Component
public class ContextEngine {

    @Resource
    private CognitiveMemoryService cognitiveMemoryService;

    private static final int DEFAULT_TOKEN_BUDGET = 2000;
    private static final int CHARS_PER_TOKEN = 2; // rough estimate for Chinese

    public ContextPacket build(String chatId, String userId, String userMessage,
                               Map<String, Object> emotionProfile) {
        long start = System.currentTimeMillis();

        // 1. Gather — collect candidate context items
        List<MemoryItem> recentMemories = cognitiveMemoryService.recallRecent(chatId, 10);
        List<MemoryItem> importantMemories =
                cognitiveMemoryService.recallByImportance(chatId, 0.6, 5);
        UserProfile profile = cognitiveMemoryService.getUserProfile(
                userId != null ? userId : chatId);

        // Merge and deduplicate
        Set<String> seen = new HashSet<>();
        List<MemoryItem> candidates = new ArrayList<>();
        for (MemoryItem m : recentMemories) {
            if (seen.add(m.getId())) candidates.add(m);
        }
        for (MemoryItem m : importantMemories) {
            if (seen.add(m.getId())) candidates.add(m);
        }

        // 2. Select — score and filter (relevance * 0.7 + recency * 0.3)
        long now = System.currentTimeMillis();
        List<MemoryItem> selected = candidates.stream()
                .peek(m -> {
                    double recency = Math.max(0,
                            1.0 - (now - m.getTimestamp().toEpochMilli()) / (3600_000.0 * 24));
                    double relevance = m.getImportance();
                    m.setScore(relevance * 0.7 + recency * 0.3);
                })
                .filter(m -> m.getScore() > 0.2)
                .sorted(Comparator.comparingDouble(MemoryItem::getScore).reversed())
                .toList();

        // 3. Structure
        String profileSummary = buildProfileSummary(profile);
        String emotionSummary = buildEmotionSummary(emotionProfile);
        List<String> memoryTexts = selected.stream()
                .map(m -> String.format("[%s] %s", m.getRole(), m.getContent()))
                .toList();

        // 4. Compress — trim to token budget
        int usedTokens = estimateTokens(profileSummary) + estimateTokens(emotionSummary);
        List<String> fittedMemories = new ArrayList<>();
        for (String text : memoryTexts) {
            int tokens = estimateTokens(text);
            if (usedTokens + tokens > DEFAULT_TOKEN_BUDGET) break;
            fittedMemories.add(text);
            usedTokens += tokens;
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[ContextEngine-build] chatId={}, candidates={}, selected={}, fitted={}, estimatedTokens={}, costMs={}",
                chatId, candidates.size(), selected.size(), fittedMemories.size(), usedTokens, cost);

        return ContextPacket.builder()
                .userProfileSummary(profileSummary)
                .emotionSummary(emotionSummary)
                .relevantMemories(fittedMemories)
                .estimatedTokens(usedTokens)
                .metadata(Map.of("candidateCount", candidates.size(),
                        "selectedCount", selected.size()))
                .build();
    }

    private String buildProfileSummary(UserProfile profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder();
        if (profile.getNickname() != null) {
            sb.append("昵称：").append(profile.getNickname()).append("；");
        }
        if (profile.getRelationshipStatus() != null) {
            sb.append("关系状态：").append(profile.getRelationshipStatus()).append("；");
        }
        if (profile.getPreferences() != null && !profile.getPreferences().isEmpty()) {
            sb.append("偏好：").append(String.join("、", profile.getPreferences())).append("；");
        }
        return sb.toString();
    }

    private String buildEmotionSummary(Map<String, Object> emotionProfile) {
        if (emotionProfile == null) return "情绪状态未知";
        Object summary = emotionProfile.get("narrativeSummary");
        return summary != null ? String.valueOf(summary) : "情绪状态未知";
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / CHARS_PER_TOKEN;
    }
}
