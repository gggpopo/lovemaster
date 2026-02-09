package com.yupi.yuaiagent.agent.model;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


// 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
// 初始化Ark客户端，从环境变量中读取您的API Key
public class ChatCompletionsVisionExample {
    // 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改

    public static void main(String[] args) {
        // 读取环境变量，避免把敏感信息写死在代码里
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("缺少环境变量 ARK_API_KEY");
        }

        // 默认路径（可通过环境变量覆盖）
        String baseUrl = System.getenv().getOrDefault("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3");
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(baseUrl).apiKey(apiKey).build();

        System.out.println("----- image input -----");
        final List<ChatMessage> messages = new ArrayList<>();
        final List<ChatCompletionContentPart> multiParts = new ArrayList<>();
        multiParts.add(ChatCompletionContentPart.builder().type("image_url").imageUrl(
                new ChatCompletionContentPart.ChatCompletionContentPartImageURL(
                        "https://ark-project.tos-cn-beijing.ivolces.com/images/view.jpeg"
                )
        ).build());
        multiParts.add(ChatCompletionContentPart.builder().type("text").text(
                "这是哪里？"
        ).build());

        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER)
                .multiContent(multiParts).build();
        messages.add(userMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model(System.getenv().getOrDefault("ARK_MODEL", "doubao-seed-1-8-251228"))
                .messages(messages)
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(choice -> System.out.println(choice.getMessage().getContent()));

        service.shutdownExecutor();
    }
}
