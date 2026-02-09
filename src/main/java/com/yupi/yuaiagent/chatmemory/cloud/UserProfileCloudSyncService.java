package com.yupi.yuaiagent.chatmemory.cloud;

import com.yupi.yuaiagent.chatmemory.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户画像异步上云（火山云端记忆库）。
 * <p>
 * 设计目标：不阻塞主请求、失败可忽略、尽量减少频繁写入。
 */
@Slf4j
@Service
public class UserProfileCloudSyncService {

    private record Fingerprint(long hash, Instant syncedAt) {
    }

    private final CloudMemoryProperties props;
    private final CloudMemoryWriteClient writeClient;
    private final ConcurrentHashMap<String, Fingerprint> lastSync = new ConcurrentHashMap<>();

    public UserProfileCloudSyncService(CloudMemoryProperties props, CloudMemoryWriteClient writeClient) {
        this.props = props;
        this.writeClient = writeClient;
    }

    @Async("cloudMemoryExecutor")
    public void syncProfileAsync(UserProfile profile) {
        if (profile == null || profile.getUserId() == null || profile.getUserId().isBlank()) {
            return;
        }
        if (!props.isEnabled()) {
            return;
        }

        String userId = profile.getUserId();
        String content = toSearchableText(profile);
        long hash = Objects.hash(content);

        long minIntervalMs = Math.max(0, props.getProfileMinSyncIntervalMs());
        Fingerprint prev = lastSync.get(userId);
        if (prev != null) {
            boolean same = prev.hash == hash;
            boolean tooFrequent = minIntervalMs > 0
                    && prev.syncedAt != null
                    && prev.syncedAt.plusMillis(minIntervalMs).isAfter(Instant.now());
            if (same || tooFrequent) {
                return;
            }
        }

        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("user_id", userId);
            if (profile.getTenantId() != null) {
                metadata.put("tenant_id", profile.getTenantId());
            }
            if (profile.getNickname() != null) {
                metadata.put("nickname", profile.getNickname());
            }
            metadata.put("memory_type", "profile_v1");
            metadata.put("time", System.currentTimeMillis());

            writeClient.upsertProfileMemory(userId, content, metadata);
            lastSync.put(userId, new Fingerprint(hash, Instant.now()));
        } catch (Exception e) {
            // 异步链路：失败不影响主流程
            log.debug("用户画像上云失败 userId={}", userId, e);
        }
    }

    /**
     * 转成更适合“检索/召回”的文本，而不是直接存全量 JSON。
     */
    static String toSearchableText(UserProfile p) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户画像");
        if (p.getNickname() != null && !p.getNickname().isBlank()) {
            sb.append(" | 昵称:").append(p.getNickname());
        }
        if (p.getGender() != null && !p.getGender().isBlank()) {
            sb.append(" | 性别:").append(p.getGender());
        }
        if (p.getAgeGroup() != null && !p.getAgeGroup().isBlank()) {
            sb.append(" | 年龄段:").append(p.getAgeGroup());
        }
        if (p.getRelationshipStatus() != null && !p.getRelationshipStatus().isBlank()) {
            sb.append(" | 感情状态:").append(p.getRelationshipStatus());
        }
        if (p.getPreferredTone() != null && !p.getPreferredTone().isBlank()) {
            sb.append(" | 偏好语气:").append(p.getPreferredTone());
        }
        if (p.getTags() != null && !p.getTags().isEmpty()) {
            sb.append(" | 标签:").append(p.getTags());
        }
        if (p.getAttributes() != null && !p.getAttributes().isEmpty()) {
            sb.append(" | 扩展:").append(p.getAttributes());
        }
        sb.append(" | 对话次数:").append(p.getConversationCount());
        return sb.toString();
    }
}

