package com.yupi.yuaiagent.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.chatmemory.cloud.UserProfileCloudSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * 用户画像服务
 * <p>
 * 提供用户画像的存储、读取、更新功能
 */
@Slf4j
@Service
public class UserProfileService {

    private static final String KEY_PREFIX = "user:profile:";

    /**
     * Redis 存储（可选）：启用 app.memory.redis.enabled=true 时由 RedisConfig 提供。
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 异步上云（可选）：启用 app.memory.cloud.enabled=true 且配置 token 时生效。
     */
    @Autowired(required = false)
    private UserProfileCloudSyncService cloudSyncService;

    @Value("${app.memory.redis.profile-ttl-days:30}")
    private long profileTtlDays;

    @Value("${app.memory.profile.local-dir:./tmp/user-profiles}")
    private String localDir;

    private final ObjectMapper objectMapper;

    public UserProfileService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 获取用户画像，不存在则创建新的
     */
    public UserProfile getOrCreateProfile(String userId) {
        UserProfile profile = getProfile(userId);
        if (profile == null) {
            profile = new UserProfile(userId);
            saveProfile(profile);
        }
        return profile;
    }

    /**
     * 获取用户画像
     */
    public UserProfile getProfile(String userId) {
        // 1) 优先从 Redis 读取（如果启用）
        UserProfile redisProfile = readFromRedis(userId);
        if (redisProfile != null) {
            return redisProfile;
        }

        // 2) 从本地文件读取（作为兜底的本地持久化）
        UserProfile localProfile = readFromLocalFile(userId);
        if (localProfile != null) {
            // 回填 Redis（如果启用）
            writeToRedis(localProfile);
        }
        return localProfile;
    }

    /**
     * 保存用户画像
     */
    public void saveProfile(UserProfile profile) {
        if (profile == null || profile.getUserId() == null) {
            return;
        }

        // 1) 本地落盘（不依赖 Redis，确保“本地 + 可异步上云”的基础能力）
        writeToLocalFile(profile);

        // 2) 写入 Redis（如果启用）
        writeToRedis(profile);

        // 3) 异步上云（失败不影响主流程）
        if (cloudSyncService != null) {
            cloudSyncService.syncProfileAsync(profile);
        }
    }

    /**
     * 更新用户画像
     */
    public void updateProfile(String userId, java.util.function.Consumer<UserProfile> updater) {
        UserProfile profile = getOrCreateProfile(userId);
        updater.accept(profile);
        saveProfile(profile);
    }

    /**
     * 删除用户画像
     */
    public void deleteProfile(String userId) {
        String key = KEY_PREFIX + userId;
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        }
        deleteLocalFile(userId);
    }

    /**
     * 记录一次对话
     */
    public void recordConversation(String userId) {
        updateProfile(userId, UserProfile::incrementConversationCount);
    }

    private UserProfile readFromRedis(String userId) {
        if (redisTemplate == null) {
            return null;
        }
        String key = KEY_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            String json = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, UserProfile.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize user profile from Redis for userId: {}", userId, e);
            return null;
        }
    }

    private void writeToRedis(UserProfile profile) {
        if (redisTemplate == null) {
            return;
        }
        String key = KEY_PREFIX + profile.getUserId();
        try {
            String json = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(key, json, profileTtlDays, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user profile to Redis for userId: {}", profile.getUserId(), e);
        } catch (Exception e) {
            log.warn("Failed to write user profile to Redis for userId: {}", profile.getUserId(), e);
        }
    }

    private UserProfile readFromLocalFile(String userId) {
        try {
            Path file = getLocalFilePath(userId);
            if (!Files.exists(file)) {
                return null;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, UserProfile.class);
        } catch (IOException e) {
            log.warn("Failed to read user profile from local file for userId: {}", userId, e);
            return null;
        }
    }

    private void writeToLocalFile(UserProfile profile) {
        try {
            Path file = getLocalFilePath(profile.getUserId());
            Files.createDirectories(file.getParent());

            // 原子写：先写临时文件，再替换
            Path tmp = Paths.get(file.toString() + ".tmp");
            String json = objectMapper.writeValueAsString(profile);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.warn("Failed to write user profile to local file for userId: {}", profile.getUserId(), e);
        }
    }

    private void deleteLocalFile(String userId) {
        try {
            Path file = getLocalFilePath(userId);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("Failed to delete local user profile file for userId: {}", userId, e);
        }
    }

    private Path getLocalFilePath(String userId) {
        // 简单路径规整，避免 userId 里出现路径分隔符
        String safe = userId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get(localDir).resolve(safe + ".json");
    }
}
