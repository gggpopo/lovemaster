package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.conversation.model.PersonaEntity;
import com.yupi.yuaiagent.conversation.service.PersonaService;
import com.yupi.yuaiagent.dto.PersonaUpsertRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * Persona 资源接口。
 */
@Slf4j
@RestController
@RequestMapping("/personas")
public class PersonaController {

    @Resource
    private PersonaService personaService;

    @GetMapping
    public List<PersonaEntity> listPersonas() {
        log.info("[PersonaController-listPersonas] {}", kv("status", "start"));
        return personaService.list();
    }

    @PostMapping
    public PersonaEntity createPersona(@RequestBody PersonaUpsertRequest request) {
        log.info("[PersonaController-createPersona] {}",
                kv("name", request == null ? "" : request.getName()));
        return personaService.create(request);
    }

    @PutMapping("/{personaId}")
    public PersonaEntity updatePersona(@PathVariable String personaId,
                                       @RequestBody PersonaUpsertRequest request) {
        log.info("[PersonaController-updatePersona] {}",
                kv("personaId", personaId, "name", request == null ? "" : request.getName()));
        return personaService.update(personaId, request);
    }

    @DeleteMapping("/{personaId}")
    public Map<String, Object> deletePersona(@PathVariable String personaId) {
        boolean deleted = personaService.delete(personaId);
        log.info("[PersonaController-deletePersona] {}",
                kv("personaId", personaId, "deleted", deleted));
        return Map.of(
                "personaId", personaId,
                "deleted", deleted
        );
    }
}
