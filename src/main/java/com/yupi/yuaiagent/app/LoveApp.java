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
import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

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
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
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
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                // 前端通过 [DONE] 判断流式结束
                .concatWithValues("[DONE]");
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

        return chatClient
                .prompt()
                .system(VISION_SYSTEM_PROMPT)
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
                .concatWithValues("[DONE]");
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
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Resource(name = "loveAppVectorStore")
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

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
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
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
        log.info("content: {}", content);
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
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
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
        log.info("content: {}", content);
        return content;
    }
}
