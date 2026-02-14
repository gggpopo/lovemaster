package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.CloudMemoryAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import com.yupi.yuaiagent.chatmemory.TieredChatMemoryAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.MimeTypeUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    /**
     * 用户态工具调用的安全白名单：
     * - 只允许与恋爱咨询/地点推荐相关的低风险工具
     * - 避免 expose doTerminate/终端/文件系统等“任务型工具”干扰用户问答
     */
    private static final Set<String> SAFE_CHAT_TOOL_NAMES = Set.of(
            "searchDateLocations",
            "queryWeather",
            "detectEmotion",
            "searchWeb",
            "recommendSticker",
            "checkContentSafety",
            "filterSensitiveContent",
            "convertTone",
            "listToneStyles",
            "calculateAnniversary",
            "getUpcomingFestivals",
            "suggestDateByDate"
    );

    /**
     * 编排层工具别名 -> 实际 Tool 方法名
     */
    private static final Map<String, Set<String>> TOOL_ALIAS_NAME_MAP = Map.of(
            "dateLocation", Set.of("searchDateLocations"),
            "weather", Set.of("queryWeather"),
            "emotionDetection", Set.of("detectEmotion"),
            "webSearch", Set.of("searchWeb"),
            "giftRecommend", Set.of("searchWeb"),
            "imageSearch", Set.of("searchWeb")
    );

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。" +
            "当用户询问去哪约会或推荐餐厅/景点/咖啡厅等地点时，优先调用约会地点推荐工具，输出带图片的地点卡片信息。";

    private static final String VISION_SYSTEM_PROMPT = """
            你是一个聊天记录分析专家，同时也是深耕恋爱心理领域的专家。请仔细分析用户上传的聊天截图。

            你需要先在内部把截图里的对话整理成结构化信息（仅用于思考，不要在最终回复中输出）。
            **内部 JSON schema（禁止直接输出）**:
            {
              "messages": [
                {
                  "sender": "string",
                  "direction": "sent|received",
                  "text": "string",
                  "timestamp": "string",
                  "has_sticker": "boolean"
                }
              ]
            }

            **判断规则（内部使用）**:
            - 根据聊天气泡的位置（通常右边是自己，左边是对方）来判断 direction；sent 表示我发送的，received 表示我收到的。
            - 提取每个气泡内的所有文字作为 text。
            - 如果气泡旁边有时间，提取为 timestamp。
            - 如果有无法用文字描述的图片、动画表情或贴纸，将 has_sticker 设为 true。

            **最终输出要求（对用户可见）**:
            - 最终回复必须以 `### 恋爱专家分析与建议` 开头。
            - 只输出用户关心的“分析与建议”，不要输出任何 JSON/代码块/逐条拆解的消息列表。
            """;

    /**
     * 视觉输出过滤：模型可能先输出“图片分解/消息 JSON”，前端只需要用户可读的“分析与建议”。
     * 规则：
     * 1) 等待出现固定标题（如：### 恋爱专家分析与建议），从该处开始向下游输出。
     * 2) 若迟迟未出现标题，缓冲达到阈值后回退输出（尽量从 ### 开始截断）。
     */
    private static Flux<String> keepUserFacingVisionPart(Flux<String> source) {
        return Flux.defer(() -> Flux.create(sink -> {
            final String[] markers = {
                    "### 恋爱专家分析与建议",
                    "###恋爱专家分析与建议",
                    "恋爱专家分析与建议",
                    "### 恋爱心理分析与建议",
                    "###恋爱心理分析与建议",
                    "恋爱心理分析与建议"
            };
            final int maxBufferBeforeFallback = 8000;

            final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);
            final StringBuilder buffer = new StringBuilder();

            reactor.core.Disposable disposable = source.subscribe(
                    chunk -> {
                        if (chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        if (started.get()) {
                            sink.next(chunk);
                            return;
                        }

                        buffer.append(chunk);
                        String buf = buffer.toString();

                        int idx = -1;
                        for (String marker : markers) {
                            idx = buf.indexOf(marker);
                            if (idx >= 0) {
                                break;
                            }
                        }

                        if (idx >= 0) {
                            started.set(true);
                            sink.next(buf.substring(idx));
                            buffer.setLength(0);
                            return;
                        }

                        if (buffer.length() >= maxBufferBeforeFallback) {
                            started.set(true);
                            // 尽量从第一个 markdown 标题开始截断
                            int h = buf.indexOf("\n###");
                            if (h >= 0) {
                                sink.next(buf.substring(h + 1));
                            } else {
                                sink.next(buf);
                            }
                            buffer.setLength(0);
                        }
                    },
                    sink::error,
                    () -> {
                        if (!started.get() && buffer.length() > 0) {
                            String buf = buffer.toString();
                            int h = buf.indexOf("\n###");
                            sink.next(h >= 0 ? buf.substring(h + 1) : buf);
                        }
                        sink.complete();
                    }
            );

            sink.onCancel(disposable::dispose);
        }));
    }

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel 聊天模型
     * @param chatMemoryRepository 可选的 ChatMemoryRepository（如 Redis 实现）
     */
    @Autowired
    public LoveApp(ChatModel dashscopeChatModel,
                   @Autowired(required = false) ChatMemoryRepository chatMemoryRepository,
                   @Autowired(required = false) CloudMemoryAdvisor cloudMemoryAdvisor,
                   @Autowired(required = false) TieredChatMemoryAdvisor tieredChatMemoryAdvisor) {

        List<org.springframework.ai.chat.client.advisor.api.Advisor> advisors = new ArrayList<>();

        // 三层记忆（优先使用）；若未装配，则回退为默认滑动窗口记忆
        if (tieredChatMemoryAdvisor != null) {
            advisors.add(tieredChatMemoryAdvisor);
        } else {
            ChatMemoryRepository repository = chatMemoryRepository != null
                    ? chatMemoryRepository
                    : new InMemoryChatMemoryRepository();
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(repository)
                    .maxMessages(20)
                    .build();
            advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }

        // 云端记忆增强（默认不启用，内部会检查 app.memory.cloud.enabled）
        if (cloudMemoryAdvisor != null) {
            advisors.add(cloudMemoryAdvisor);
        }
        // 自定义日志 Advisor，可按需开启
        advisors.add(new MyLoggerAdvisor());
//        // 自定义推理增强 Advisor，可按需开启
//        advisors.add(new ReReadingAdvisor());

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(advisors.toArray(org.springframework.ai.chat.client.advisor.api.Advisor[]::new))
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        log.info("[LoveApp-doChat] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "message", message));
        long start = System.currentTimeMillis();
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        long cost = System.currentTimeMillis() - start;
        log.info("[LoveApp-doChat] {}",
                kv("chatId", chatId,
                        "durationMs", cost,
                        "responseLength", content == null ? 0 : content.length(),
                        "response", content));
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return doChatByStream(message, chatId, null);
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输，支持动态系统提示词）
     *
     * @param message      用户消息
     * @param chatId       会话 ID
     * @param systemPrompt 动态系统提示词（可选）
     * @return SSE 流
     */
    public Flux<String> doChatByStream(String message, String chatId, String systemPrompt) {
        log.info("[LoveApp-doChatByStream] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "customSystemPrompt", hasText(systemPrompt),
                        "systemPromptLength", systemPrompt == null ? 0 : systemPrompt.length(),
                        "message", message));
        long start = System.currentTimeMillis();
        if (hasText(systemPrompt)) {
            return chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content()
                    // 前端通过 [DONE] 判断流式结束
                    .concatWithValues("[DONE]")
                    .doOnComplete(() -> log.info("[LoveApp-doChatByStream] {}",
                            kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                    .doOnError(e -> log.error("[LoveApp-doChatByStream] {}",
                            kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
        }
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                // 前端通过 [DONE] 判断流式结束
                .concatWithValues("[DONE]")
                .doOnComplete(() -> log.info("[LoveApp-doChatByStream] {}",
                        kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("[LoveApp-doChatByStream] {}",
                        kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
    }

    /**
     * AI 多模态对话（支持图片理解，SSE 流式传输）
     *
     * @param message 用户消息
     * @param chatId  会话ID
     * @param images  Base64 编码的图片列表
     * @return
     */
    public Flux<String> doChatWithVision(String message, String chatId, List<String> images) {
        return doChatWithVision(message, chatId, images, null);
    }

    /**
     * AI 多模态对话（支持图片理解，SSE 流式传输，支持动态系统提示词）
     *
     * @param message             用户消息
     * @param chatId              会话ID
     * @param images              Base64 编码的图片列表
     * @param additionalSystemMsg 动态系统提示词（可选）
     * @return SSE 流
     */
    public Flux<String> doChatWithVision(String message, String chatId, List<String> images, String additionalSystemMsg) {
        int imageCount = images == null ? 0 : images.size();
        log.info("[LoveApp-doChatWithVision] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "customSystemPrompt", hasText(additionalSystemMsg),
                        "systemPromptLength", additionalSystemMsg == null ? 0 : additionalSystemMsg.length(),
                        "imageCount", imageCount));
        long start = System.currentTimeMillis();
        // 构建多模态内容
        List<Object> contentList = new ArrayList<>();

        // 添加图片
        if (images != null && !images.isEmpty()) {
            for (String imageData : images) {
                // 解析 Base64 图片数据
                // 格式: data:image/jpeg;base64,/9j/4AAQ...
                String mimeType = "image/jpeg";
                String base64Data = imageData;

                if (imageData.startsWith("data:")) {
                    int commaIndex = imageData.indexOf(",");
                    if (commaIndex > 0) {
                        String header = imageData.substring(5, commaIndex);
                        int semicolonIndex = header.indexOf(";");
                        if (semicolonIndex > 0) {
                            mimeType = header.substring(0, semicolonIndex);
                        }
                        base64Data = imageData.substring(commaIndex + 1);
                    }
                }

                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                Media media = Media.builder()
                        .mimeType(org.springframework.util.MimeType.valueOf(mimeType))
                        .data(imageBytes)
                        .build();
                contentList.add(media);
            }
        }

        // 添加文本消息
        String userText = (message == null || message.isBlank()) ? "请分析这张图片" : message;
        String visionSystemPrompt = buildVisionSystemPrompt(additionalSystemMsg);

        return chatClient
                .prompt()
                .system(visionSystemPrompt)
                .user(userSpec -> {
                    for (Object content : contentList) {
                        if (content instanceof Media) {
                            userSpec.media((Media) content);
                        }
                    }
                    userSpec.text(userText);
                })
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .transform(LoveApp::keepUserFacingVisionPart)
                // 前端通过 [DONE] 判断流式结束
                .concatWithValues("[DONE]")
                .doOnComplete(() -> log.info("[LoveApp-doChatWithVision] {}",
                        kv("chatId", chatId, "status", "completed", "durationMs", System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("[LoveApp-doChatWithVision] {}",
                        kv("chatId", chatId, "status", "error", "durationMs", System.currentTimeMillis() - start), e));
    }

    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        log.info("[LoveApp-doChatWithReport] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "message", message));
        long start = System.currentTimeMillis();
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        long cost = System.currentTimeMillis() - start;
        log.info("[LoveApp-doChatWithReport] {}",
                kv("chatId", chatId,
                        "durationMs", cost,
                        "suggestionCount", loveReport == null || loveReport.suggestions() == null ? 0 : loveReport.suggestions().size(),
                        "loveReport", loveReport));
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Resource(name = "loveAppVectorStore")
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource(name = "loveAppRagAdvisor")
    private Advisor loveAppRagAdvisor;

    @Autowired(required = false)
    @Qualifier("pgVectorVectorStore")
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        log.info("[LoveApp-doChatWithRag] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "message", message));
        long start = System.currentTimeMillis();
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.info("[LoveApp-doChatWithRag] {}",
                kv("chatId", chatId,
                        "rewrittenMessageLength", rewrittenMessage == null ? 0 : rewrittenMessage.length(),
                        "rewrittenMessage", rewrittenMessage));
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(loveAppRagAdvisor)
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        long cost = System.currentTimeMillis() - start;
        log.info("[LoveApp-doChatWithRag] {}",
                kv("chatId", chatId,
                        "durationMs", cost,
                        "responseLength", content == null ? 0 : content.length(),
                        "response", content));
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        return doChatWithTools(message, chatId, Set.of(), null);
    }

    /**
     * AI 恋爱报告功能（支持调用工具 + 按编排建议收敛工具范围）
     *
     * @param message            用户消息
     * @param chatId             会话 ID
     * @param suggestedToolAlias 编排层建议工具别名（例如 dateLocation / weather）
     * @return 模型回复
     */
    public String doChatWithTools(String message, String chatId, Set<String> suggestedToolAlias) {
        return doChatWithTools(message, chatId, suggestedToolAlias, null);
    }

    /**
     * AI 恋爱报告功能（支持调用工具 + 按编排建议收敛工具范围 + 动态系统提示词）
     *
     * @param message            用户消息
     * @param chatId             会话 ID
     * @param suggestedToolAlias 编排层建议工具别名（例如 dateLocation / weather）
     * @param systemPrompt       动态系统提示词（可选）
     * @return 模型回复
     */
    public String doChatWithTools(String message, String chatId, Set<String> suggestedToolAlias, String systemPrompt) {
        ToolCallback[] selectedTools = selectToolsForToolChat(allTools, suggestedToolAlias);
        List<String> selectedToolNames = extractToolNames(selectedTools);

        log.info("[LoveApp-doChatWithTools] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "toolCount", allTools == null ? 0 : allTools.length,
                        "selectedToolCount", selectedTools.length,
                        "selectedTools", selectedToolNames,
                        "customSystemPrompt", hasText(systemPrompt),
                        "systemPromptLength", systemPrompt == null ? 0 : systemPrompt.length(),
                        "suggestedToolAlias", suggestedToolAlias,
                        "message", message));
        long start = System.currentTimeMillis();
        ChatResponse chatResponse;
        if (hasText(systemPrompt)) {
            chatResponse = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    // 开启日志，便于观察效果
                    .advisors(new MyLoggerAdvisor())
                    .toolCallbacks(selectedTools)
                    .call()
                    .chatResponse();
        } else {
            chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    // 开启日志，便于观察效果
                    .advisors(new MyLoggerAdvisor())
                    .toolCallbacks(selectedTools)
                    .call()
                    .chatResponse();
        }
        String content = chatResponse.getResult().getOutput().getText();
        long cost = System.currentTimeMillis() - start;
        log.info("[LoveApp-doChatWithTools] {}",
                kv("chatId", chatId,
                        "durationMs", cost,
                        "responseLength", content == null ? 0 : content.length(),
                        "response", content));
        return content;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String buildVisionSystemPrompt(String additionalSystemMsg) {
        if (!hasText(additionalSystemMsg)) {
            return VISION_SYSTEM_PROMPT;
        }
        return VISION_SYSTEM_PROMPT + "\n\n【场景化补充要求】\n" + additionalSystemMsg;
    }

    static ToolCallback[] selectToolsForToolChat(ToolCallback[] allTools, Set<String> suggestedToolAlias) {
        if (allTools == null || allTools.length == 0) {
            return new ToolCallback[0];
        }

        List<ToolCallback> safeTools = new ArrayList<>();
        for (ToolCallback tool : allTools) {
            String toolName = safeToolName(tool);
            if (SAFE_CHAT_TOOL_NAMES.contains(toolName)) {
                safeTools.add(tool);
            }
        }

        if (safeTools.isEmpty()) {
            return allTools;
        }

        Set<String> preferredAliases = suggestedToolAlias == null
                ? Set.of()
                : suggestedToolAlias.stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (preferredAliases.isEmpty()) {
            return safeTools.toArray(new ToolCallback[0]);
        }

        Set<String> preferredToolNames = new LinkedHashSet<>();
        for (String alias : preferredAliases) {
            String normalized = alias.trim();
            preferredToolNames.add(normalized);
            preferredToolNames.addAll(TOOL_ALIAS_NAME_MAP.getOrDefault(normalized, Set.of()));
        }

        List<ToolCallback> preferredTools = new ArrayList<>();
        for (ToolCallback tool : safeTools) {
            String toolName = safeToolName(tool);
            if (preferredToolNames.contains(toolName)) {
                preferredTools.add(tool);
            }
        }

        if (!preferredTools.isEmpty()) {
            return preferredTools.toArray(new ToolCallback[0]);
        }
        return safeTools.toArray(new ToolCallback[0]);
    }

    private static String safeToolName(ToolCallback tool) {
        if (tool == null || tool.getToolDefinition() == null || tool.getToolDefinition().name() == null) {
            return "";
        }
        return tool.getToolDefinition().name();
    }

    private static List<String> extractToolNames(ToolCallback[] tools) {
        List<String> names = new ArrayList<>();
        if (tools == null) {
            return names;
        }
        for (ToolCallback tool : tools) {
            String name = safeToolName(tool);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        log.info("[LoveApp-doChatWithMcp] {}",
                kv("chatId", chatId,
                        "messageLength", message == null ? 0 : message.length(),
                        "message", message));
        long start = System.currentTimeMillis();
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        long cost = System.currentTimeMillis() - start;
        log.info("[LoveApp-doChatWithMcp] {}",
                kv("chatId", chatId,
                        "durationMs", cost,
                        "responseLength", content == null ? 0 : content.length(),
                        "response", content));
        return content;
    }
}
