package com.yupi.yuaiagent.memory.cognitive;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.*;

@Data
@Builder
public class UserProfile {
    private String userId;
    private String nickname;
    private String relationshipStatus;  // single, dating, married, complicated
    private String partnerDescription;
    private List<String> preferences;   // hobbies, food, etc.
    private List<String> avoidTopics;
    private Map<String, Object> customFields;
    private Instant lastUpdated;
}
