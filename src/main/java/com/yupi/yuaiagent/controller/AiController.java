package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.dto.VisionChatRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel chatModel;

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        log.info("[AiController-doChatWithLoveAppSync] {}",
                kv("chatId", chatId, "messageLength", message == null ? 0 : message.length(), "message", message));
        long start = System.currentTimeMillis();
        String result = loveApp.doChat(message, chatId);
        long cost = System.currentTimeMillis() - start;
        log.info("[AiController-doChatWithLoveAppSync] {}",
                kv("chatId", chatId, "durationMs", cost, "responseLength", result == null ? 0 : result.length(), "response", result));
        return result;
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        log.info("[AiController-doChatWithLoveAppSSE] {}",
                kv("chatId", chatId, "messageLength", message == null ? 0 : message.length(), "message", message));
        long start = System.currentTimeMillis();
        return loveApp.doChatByStream(message, chatId)
                .doOnComplete(() -> log.info("[AiController-doChatWithLoveAppSSE] {}",
                        kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("[AiController-doChatWithLoveAppSSE] {}",
                        kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        log.info("[AiController-doChatWithLoveAppServerSentEvent] {}",
                kv("chatId", chatId, "messageLength", message == null ? 0 : message.length(), "message", message));
        long start = System.currentTimeMillis();
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .doOnComplete(() -> log.info("[AiController-doChatWithLoveAppServerSentEvent] {}",
                        kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("[AiController-doChatWithLoveAppServerSentEvent] {}",
                        kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        log.info("[AiController-doChatWithLoveAppServerSseEmitter] {}",
                kv("chatId", chatId, "messageLength", message == null ? 0 : message.length(), "message", message));
        long start = System.currentTimeMillis();
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        log.error("[AiController-doChatWithLoveAppServerSseEmitter] {}",
                                kv("chatId", chatId, "status", "send_error"), e);
                        sseEmitter.completeWithError(e);
                    }
                }, e -> {
                    log.error("[AiController-doChatWithLoveAppServerSseEmitter] {}",
                            kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e);
                    sseEmitter.completeWithError(e);
                }, () -> {
                    log.info("[AiController-doChatWithLoveAppServerSseEmitter] {}",
                            kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start));
                    sseEmitter.complete();
                });
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        log.info("[AiController-doChatWithManus] {}",
                kv("messageLength", message == null ? 0 : message.length(), "message", message));
        long start = System.currentTimeMillis();
        YuManus yuManus = new YuManus(allTools, chatModel);
        SseEmitter emitter = yuManus.runStream(message);
        emitter.onCompletion(() -> log.info("[AiController-doChatWithManus] {}",
                kv("status", "completed", "durationMs", System.currentTimeMillis() - start)));
        emitter.onTimeout(() -> log.warn("[AiController-doChatWithManus] {}",
                kv("status", "timeout", "durationMs", System.currentTimeMillis() - start)));
        emitter.onError(e -> log.error("[AiController-doChatWithManus] {}",
                kv("status", "error", "durationMs", System.currentTimeMillis() - start), e));
        return emitter;
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用（支持图片理解）
     *
     * @param request 包含 message、chatId、images 的请求体
     * @return SSE 流式响应
     */
    @PostMapping(value = "/love_app/chat/vision", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppVision(@RequestBody VisionChatRequest request) {
        String message = request == null ? null : request.getMessage();
        String chatId = request == null ? null : request.getChatId();
        int imageCount = request == null || request.getImages() == null ? 0 : request.getImages().size();
        log.info("[AiController-doChatWithLoveAppVision] {}",
                kv("chatId", chatId, "messageLength", message == null ? 0 : message.length(), "imageCount", imageCount));
        long start = System.currentTimeMillis();
        return loveApp.doChatWithVision(message, chatId, request == null ? null : request.getImages())
                .doOnComplete(() -> log.info("[AiController-doChatWithLoveAppVision] {}",
                        kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("[AiController-doChatWithLoveAppVision] {}",
                        kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
    }
}
