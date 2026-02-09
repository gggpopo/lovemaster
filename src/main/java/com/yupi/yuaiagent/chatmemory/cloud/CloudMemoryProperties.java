package com.yupi.yuaiagent.chatmemory.cloud;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 云端记忆库配置。
 * <p>
 * 注意：token 请通过环境变量注入，避免写入仓库。
 */
@Data
@ConfigurationProperties(prefix = "app.memory.cloud")
public class CloudMemoryProperties {

    /**
     * 是否启用云端记忆检索/预取。
     */
    private boolean enabled = false;

    /**
     * 云端记忆检索接口 URL（示例：.../api/memory/search）。
     */
    private String searchUrl = "https://api-knowledgebase.mlp.cn-beijing.volces.com/api/memory/search";

    /**
     * 云端记忆写入接口 URL（示例：.../api/memory/upsert 或 .../api/memory/add）。
     */
    private String upsertUrl = "https://api-knowledgebase.mlp.cn-beijing.volces.com/api/memory/upsert";

    /**
     * Bearer token（建议使用环境变量：VOLC_MEMORY_TOKEN）。
     */
    private String token;

    /**
     * collection 名称。
     */
    private String collectionName = "agent_learn";

    /**
     * 返回条数。
     */
    private int limit = 10;

    /**
     * 记忆类型过滤。
     */
    private List<String> memoryType = new ArrayList<>(List.of("event_v1", "profile_v1"));

    /**
     * metadata 默认值（可选）。
     */
    private String defaultUserId;
    private String defaultUserName;
    private String defaultAssistantId;
    private String defaultAssistantName;

    /**
     * 写入到提示词的最大字符数（防止长文本撑爆上下文）。
     */
    private int maxContextChars = 1600;

    /**
     * HTTP 超时（毫秒）。
     */
    private int connectTimeoutMs = 1500;
    private int readTimeoutMs = 3000;

    /**
     * 用户画像异步上云：最小同步间隔（毫秒），用于削峰；0 表示不限制。
     */
    private long profileMinSyncIntervalMs = 5000;
}
