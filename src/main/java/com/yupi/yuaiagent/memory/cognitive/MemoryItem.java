package com.yupi.yuaiagent.memory.cognitive;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class MemoryItem {
    private String id;
    private String chatId;
    private String role;        // user / assistant
    private String content;
    private Instant timestamp;
    private double importance;   // 0.0 - 1.0
    private String emotionType;  // HAPPY, SAD, etc.
    private double emotionIntensity;
    private Map<String, Object> metadata;
    private double score;        // retrieval score (computed at query time)
}
