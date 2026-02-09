package com.yupi.yuaiagent.chatmemory;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户画像实体
 * <p>
 * 存储用户的偏好、特征等信息，用于个性化对话
 */
@Data
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 性别：male / female / unknown
     */
    private String gender;

    /**
     * 年龄段：teen / young / middle / senior
     */
    private String ageGroup;

    /**
     * 感情状态：single / dating / married / complicated
     */
    private String relationshipStatus;

    /**
     * 偏好的沟通风格：formal / casual / humorous / romantic
     */
    private String preferredTone;

    /**
     * 用户标签（如：内向、浪漫、理性等）
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * 扩展属性
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 对话次数
     */
    private int conversationCount;

    public UserProfile() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserProfile(String userId) {
        this();
        this.userId = userId;
    }

    public void incrementConversationCount() {
        this.conversationCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void addTag(String key, String value) {
        this.tags.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }
}
