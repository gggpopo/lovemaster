package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.conversation.model.ConversationEntity;
import com.yupi.yuaiagent.conversation.service.ConversationStoreService;
import com.yupi.yuaiagent.dto.ConversationCreateRequest;
import com.yupi.yuaiagent.dto.ConversationMessageRequest;
import com.yupi.yuaiagent.orchestration.v2.ConversationOrchestrationService;
import com.yupi.yuaiagent.orchestration.v3.DynamicOrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 对话资源接口（v2 主入口）。
 */
@Slf4j
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    @Resource
    private ConversationStoreService conversationStoreService;

    @Resource
    private ConversationOrchestrationService conversationOrchestrationService;

    @Resource
    private DynamicOrchestrationService dynamicOrchestrationService;

    @PostMapping
    public ConversationEntity createConversation(@RequestBody(required = false) ConversationCreateRequest request) {
        log.info("[ConversationController-createConversation] {}",
                kv("userId", request == null ? "" : request.getUserId(),
                        "personaId", request == null ? "" : request.getPersonaId(),
                        "sceneId", request == null ? "" : request.getSceneId()));
        return conversationStoreService.createConversation(request);
    }

    @GetMapping("/{conversationId}")
    public ConversationEntity getConversation(@PathVariable String conversationId) {
        log.info("[ConversationController-getConversation] {}",
                kv("conversationId", conversationId));
        return conversationStoreService.getConversation(conversationId);
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter postMessage(@PathVariable String conversationId,
                                  @RequestBody(required = false) ConversationMessageRequest request) {
        log.info("[ConversationController-postMessage] {}",
                kv("conversationId", conversationId,
                        "messageLength", request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                        "imageCount", request == null || request.getImages() == null ? 0 : request.getImages().size(),
                        "forceMode", request == null ? "" : request.getForceMode(),
                        "sceneId", request == null ? "" : request.getSceneId(),
                        "personaId", request == null ? "" : request.getPersonaId()));
        return conversationOrchestrationService.streamConversation(conversationId, request);
    }

    /**
     * V3 动态编排端点（基于 PlanAgent）
     */
    @PostMapping(value = "/{conversationId}/messages/v3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter postMessageV3(@PathVariable String conversationId,
                                    @RequestBody(required = false) ConversationMessageRequest request) {
        log.info("[ConversationController-postMessageV3] {}",
                kv("conversationId", conversationId,
                        "messageLength", request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                        "imageCount", request == null || request.getImages() == null ? 0 : request.getImages().size(),
                        "forceMode", request == null ? "" : request.getForceMode(),
                        "sceneId", request == null ? "" : request.getSceneId(),
                        "personaId", request == null ? "" : request.getPersonaId()));
        return dynamicOrchestrationService.streamConversation(conversationId, request);
    }

    @PostMapping("/{conversationId}/interrupt")
    public Map<String, Object> interrupt(@PathVariable String conversationId,
                                         @RequestParam(value = "reason", required = false) String reason) {
        boolean interrupted = conversationOrchestrationService.interrupt(conversationId, reason);
        log.info("[ConversationController-interrupt] {}",
                kv("conversationId", conversationId, "reason", reason, "interrupted", interrupted));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("interrupted", interrupted);
        result.put("reason", reason == null ? "user_interrupt" : reason);
        return result;
    }
}
