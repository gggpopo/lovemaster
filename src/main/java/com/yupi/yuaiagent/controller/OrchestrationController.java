package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.dto.OrchestrationChatRequest;
import com.yupi.yuaiagent.orchestration.OrchestrationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 统一编排 API
 */
@Slf4j
@RestController
@RequestMapping("/ai/orchestrated")
public class OrchestrationController {

    @Resource
    private OrchestrationService orchestrationService;

    /**
     * 统一编排聊天接口（SSE）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orchestratedChat(@RequestBody OrchestrationChatRequest request) {
        log.info("[OrchestrationController-orchestratedChat] {}",
                kv("chatId", request == null ? "" : request.getChatId(),
                        "messageLength", request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                        "imageCount", request == null || request.getImages() == null ? 0 : request.getImages().size(),
                        "forceMode", request == null ? "" : request.getForceMode(),
                        "sceneId", request == null ? "" : request.getSceneId()));
        return orchestrationService.orchestrate(request);
    }
}
