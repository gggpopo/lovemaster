package com.yupi.yuaiagent.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.conversation.model.ConversationEntity;
import com.yupi.yuaiagent.conversation.model.MessageEntity;
import com.yupi.yuaiagent.dto.ConversationCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 会话与消息存储服务。
 *
 * 优先写 DB；DB 不可用时自动回退到内存，保证主链路可用。
 */
@Slf4j
@Service
public class ConversationStoreService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;

    private final Map<String, ConversationEntity> memoryConversations = new ConcurrentHashMap<>();
    private final Map<String, List<MessageEntity>> memoryMessages = new ConcurrentHashMap<>();

    public ConversationStoreService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public ConversationEntity createConversation(ConversationCreateRequest request) {
        String id = safeId(request == null ? null : request.getId(), "conv_");
        String userId = safeText(request == null ? null : request.getUserId(), "anonymous");
        String title = safeText(request == null ? null : request.getTitle(), "新对话");
        String personaId = safeText(request == null ? null : request.getPersonaId(), "default");
        String sceneId = safeText(request == null ? null : request.getSceneId(), "general_relationship");
        long now = System.currentTimeMillis();
        ConversationEntity entity = ConversationEntity.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .personaId(personaId)
                .sceneId(sceneId)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        if (!saveConversationToDb(entity)) {
            memoryConversations.put(id, entity);
        }
        return entity;
    }

    public ConversationEntity ensureConversation(String conversationId,
                                                 String userId,
                                                 String personaId,
                                                 String sceneId) {
        String id = safeId(conversationId, "conv_");
        ConversationEntity existing = getConversation(id);
        if (existing != null) {
            return existing;
        }
        ConversationCreateRequest request = new ConversationCreateRequest();
        request.setId(id);
        request.setUserId(userId);
        request.setPersonaId(personaId);
        request.setSceneId(sceneId);
        request.setTitle("新对话");
        return createConversation(request);
    }

    public ConversationEntity getConversation(String conversationId) {
        String id = safeId(conversationId, "conv_");
        ConversationEntity dbEntity = queryConversationFromDb(id);
        if (dbEntity != null) {
            return dbEntity;
        }
        return memoryConversations.get(id);
    }

    public void saveMessage(String conversationId,
                            String role,
                            String content,
                            List<String> images,
                            Map<String, Object> metadata) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long now = System.currentTimeMillis();
        String imagesJson = toJson(images == null ? List.of() : images);
        String metadataJson = toJson(metadata == null ? Map.of() : metadata);
        MessageEntity messageEntity = MessageEntity.builder()
                .id(id)
                .conversationId(conversationId)
                .role(safeText(role, "assistant"))
                .content(content == null ? "" : content)
                .imagesJson(imagesJson)
                .metadataJson(metadataJson)
                .createdAt(now)
                .build();

        if (!saveMessageToDb(messageEntity)) {
            memoryMessages.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(messageEntity);
        }
    }

    public List<MessageEntity> listRecentMessages(String conversationId, int limit) {
        List<MessageEntity> dbRows = queryRecentMessagesFromDb(conversationId, limit);
        if (!dbRows.isEmpty()) {
            return dbRows;
        }
        List<MessageEntity> local = memoryMessages.getOrDefault(conversationId, List.of());
        if (local.isEmpty()) {
            return List.of();
        }
        int size = local.size();
        int from = Math.max(0, size - Math.max(limit, 1));
        return local.subList(from, size);
    }

    public void updateConversationStatus(String conversationId, String status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("update conversations set status=?, updated_at=? where id=?",
                        status, System.currentTimeMillis(), conversationId);
                return;
            } catch (Exception e) {
                log.warn("[ConversationStoreService-updateConversationStatus] {}",
                        kv("conversationId", conversationId, "status", status, "fallback", "memory"), e);
            }
        }
        ConversationEntity old = memoryConversations.get(conversationId);
        if (old != null) {
            old.setStatus(status);
            old.setUpdatedAt(System.currentTimeMillis());
        }
    }

    private boolean saveConversationToDb(ConversationEntity entity) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            jdbcTemplate.update(
                    "insert into conversations(id, user_id, persona_id, title, scene_id, status, created_at, updated_at) values(?,?,?,?,?,?,?,?) " +
                            "on conflict (id) do update set user_id=excluded.user_id, persona_id=excluded.persona_id, title=excluded.title, " +
                            "scene_id=excluded.scene_id, status=excluded.status, updated_at=excluded.updated_at",
                    entity.getId(), entity.getUserId(), entity.getPersonaId(), entity.getTitle(),
                    entity.getSceneId(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
            );
            return true;
        } catch (Exception e) {
            log.warn("[ConversationStoreService-saveConversationToDb] {}",
                    kv("conversationId", entity.getId(), "fallback", "memory"), e);
            return false;
        }
    }

    private ConversationEntity queryConversationFromDb(String conversationId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return null;
        }
        try {
            List<ConversationEntity> rows = jdbcTemplate.query(
                    "select id, user_id, persona_id, title, scene_id, status, created_at, updated_at from conversations where id = ?",
                    (rs, rowNum) -> toConversation(rs),
                    conversationId
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("[ConversationStoreService-queryConversationFromDb] {}",
                    kv("conversationId", conversationId, "fallback", "memory"), e);
            return null;
        }
    }

    private boolean saveMessageToDb(MessageEntity messageEntity) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            jdbcTemplate.update(
                    "insert into messages(id, conversation_id, role, content, images_json, metadata_json, created_at) values(?,?,?,?,?,?,?)",
                    messageEntity.getId(), messageEntity.getConversationId(), messageEntity.getRole(),
                    messageEntity.getContent(), messageEntity.getImagesJson(), messageEntity.getMetadataJson(),
                    messageEntity.getCreatedAt()
            );
            return true;
        } catch (Exception e) {
            log.warn("[ConversationStoreService-saveMessageToDb] {}",
                    kv("conversationId", messageEntity.getConversationId(), "messageId", messageEntity.getId(), "fallback", "memory"), e);
            return false;
        }
    }

    private List<MessageEntity> queryRecentMessagesFromDb(String conversationId, int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        try {
            List<MessageEntity> rows = jdbcTemplate.query(
                    "select id, conversation_id, role, content, images_json, metadata_json, created_at from messages " +
                            "where conversation_id = ? order by created_at desc limit ?",
                    (rs, rowNum) -> toMessage(rs),
                    conversationId, safeLimit
            );
            rows.sort(Comparator.comparingLong(item -> item.getCreatedAt() == null ? 0L : item.getCreatedAt()));
            return rows;
        } catch (Exception e) {
            log.warn("[ConversationStoreService-queryRecentMessagesFromDb] {}",
                    kv("conversationId", conversationId, "limit", limit, "fallback", "memory"), e);
            return List.of();
        }
    }

    private ConversationEntity toConversation(ResultSet rs) throws SQLException {
        return ConversationEntity.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .personaId(rs.getString("persona_id"))
                .title(rs.getString("title"))
                .sceneId(rs.getString("scene_id"))
                .status(rs.getString("status"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

    private MessageEntity toMessage(ResultSet rs) throws SQLException {
        return MessageEntity.builder()
                .id(rs.getString("id"))
                .conversationId(rs.getString("conversation_id"))
                .role(rs.getString("role"))
                .content(rs.getString("content"))
                .imagesJson(rs.getString("images_json"))
                .metadataJson(rs.getString("metadata_json"))
                .createdAt(rs.getLong("created_at"))
                .build();
    }

    private String safeId(String raw, String prefix) {
        if (raw == null || raw.isBlank()) {
            return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        return raw.trim();
    }

    private String safeText(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return raw.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[ConversationStoreService-toJson] {}", kv("fallback", "string"), e);
            return "{}";
        }
    }
}
