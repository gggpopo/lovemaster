package com.yupi.yuaiagent.conversation.service;

import com.yupi.yuaiagent.conversation.model.PersonaEntity;
import com.yupi.yuaiagent.dto.PersonaUpsertRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * Persona 管理服务。
 */
@Slf4j
@Service
public class PersonaService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    private final Map<String, PersonaEntity> memoryPersonas = new ConcurrentHashMap<>();

    public PersonaService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        ensureDefaultPersonaInMemory();
    }

    public List<PersonaEntity> list() {
        List<PersonaEntity> rows = listFromDb();
        if (!rows.isEmpty()) {
            return rows;
        }
        return new ArrayList<>(memoryPersonas.values());
    }

    public PersonaEntity getById(String id) {
        if (id == null || id.isBlank()) {
            return getDefaultPersona();
        }
        PersonaEntity fromDb = getFromDb(id);
        if (fromDb != null) {
            return fromDb;
        }
        PersonaEntity local = memoryPersonas.get(id);
        if (local != null) {
            return local;
        }
        return getDefaultPersona();
    }

    public PersonaEntity create(PersonaUpsertRequest request) {
        String id = "persona_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long now = System.currentTimeMillis();
        PersonaEntity entity = PersonaEntity.builder()
                .id(id)
                .name(safe(request == null ? null : request.getName(), "自定义人格"))
                .description(safe(request == null ? null : request.getDescription(), ""))
                .systemPrompt(safe(request == null ? null : request.getSystemPrompt(), defaultSystemPrompt()))
                .styleGuideJson(safe(request == null ? null : request.getStyleGuideJson(), "{}"))
                .enabled(request == null || request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (!saveToDb(entity)) {
            memoryPersonas.put(entity.getId(), entity);
        }
        return entity;
    }

    public PersonaEntity update(String id, PersonaUpsertRequest request) {
        PersonaEntity existed = getById(id);
        if (existed == null) {
            return null;
        }
        PersonaEntity updated = PersonaEntity.builder()
                .id(existed.getId())
                .name(safe(request == null ? null : request.getName(), existed.getName()))
                .description(safe(request == null ? null : request.getDescription(), existed.getDescription()))
                .systemPrompt(safe(request == null ? null : request.getSystemPrompt(), existed.getSystemPrompt()))
                .styleGuideJson(safe(request == null ? null : request.getStyleGuideJson(), existed.getStyleGuideJson()))
                .enabled(request == null || request.getEnabled() == null ? existed.getEnabled() : request.getEnabled())
                .createdAt(existed.getCreatedAt())
                .updatedAt(System.currentTimeMillis())
                .build();
        if (!saveToDb(updated)) {
            memoryPersonas.put(updated.getId(), updated);
        }
        return updated;
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank() || "default".equals(id)) {
            return false;
        }
        if (deleteFromDb(id)) {
            return true;
        }
        return memoryPersonas.remove(id) != null;
    }

    public PersonaEntity getDefaultPersona() {
        PersonaEntity fromDb = getFromDb("default");
        if (fromDb != null) {
            return fromDb;
        }
        ensureDefaultPersonaInMemory();
        return memoryPersonas.get("default");
    }

    private void ensureDefaultPersonaInMemory() {
        memoryPersonas.computeIfAbsent("default", key -> {
            long now = System.currentTimeMillis();
            return PersonaEntity.builder()
                    .id("default")
                    .name("恋爱大师")
                    .description("温柔、尊重、边界清晰的恋爱咨询助手")
                    .systemPrompt(defaultSystemPrompt())
                    .styleGuideJson("{}")
                    .enabled(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        });
    }

    private String defaultSystemPrompt() {
        return "你是深耕恋爱心理领域的专家，回答温柔、尊重、可执行，并保持边界感。";
    }

    private List<PersonaEntity> listFromDb() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    "select id, name, description, system_prompt, style_guide_json, enabled, created_at, updated_at from personas order by created_at desc",
                    (rs, rowNum) -> toEntity(rs)
            );
        } catch (Exception e) {
            log.warn("[PersonaService-listFromDb] {}", kv("fallback", "memory"), e);
            return List.of();
        }
    }

    private PersonaEntity getFromDb(String id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return null;
        }
        try {
            List<PersonaEntity> rows = jdbcTemplate.query(
                    "select id, name, description, system_prompt, style_guide_json, enabled, created_at, updated_at from personas where id = ?",
                    (rs, rowNum) -> toEntity(rs),
                    id
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("[PersonaService-getFromDb] {}", kv("personaId", id, "fallback", "memory"), e);
            return null;
        }
    }

    private boolean saveToDb(PersonaEntity entity) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            jdbcTemplate.update(
                    "insert into personas(id, name, description, system_prompt, style_guide_json, enabled, created_at, updated_at) values(?,?,?,?,?,?,?,?) " +
                            "on conflict (id) do update set name=excluded.name, description=excluded.description, system_prompt=excluded.system_prompt, " +
                            "style_guide_json=excluded.style_guide_json, enabled=excluded.enabled, updated_at=excluded.updated_at",
                    entity.getId(), entity.getName(), entity.getDescription(), entity.getSystemPrompt(),
                    entity.getStyleGuideJson(), entity.getEnabled(), entity.getCreatedAt(), entity.getUpdatedAt()
            );
            return true;
        } catch (Exception e) {
            log.warn("[PersonaService-saveToDb] {}", kv("personaId", entity.getId(), "fallback", "memory"), e);
            return false;
        }
    }

    private boolean deleteFromDb(String id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            return jdbcTemplate.update("delete from personas where id = ?", id) > 0;
        } catch (Exception e) {
            log.warn("[PersonaService-deleteFromDb] {}", kv("personaId", id, "fallback", "memory"), e);
            return false;
        }
    }

    private PersonaEntity toEntity(ResultSet rs) throws SQLException {
        return PersonaEntity.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .systemPrompt(rs.getString("system_prompt"))
                .styleGuideJson(rs.getString("style_guide_json"))
                .enabled(rs.getBoolean("enabled"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
