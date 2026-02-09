package com.yupi.yuaiagent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 统一选择项目中使用的 ChatModel。
 * <p>
 * - 默认使用 DashScope（阿里云百练）
 * - 配置 app.llm.provider=volcengine 时，优先使用 OpenAI 兼容的模型（用于火山引擎 Ark / 豆包）
 */
@Configuration
public class ChatModelSelectorConfig {

    /**
     * @param provider dashscope / volcengine
     */
    @Bean
    @Primary
    public ChatModel chatModel(
            @Qualifier("dashscopeChatModel") ObjectProvider<ChatModel> dashscopeChatModelProvider,
            @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAiChatModelProvider,
            @Value("${app.llm.provider:dashscope}") String provider
    ) {
        String p = provider == null ? "" : provider.trim();

        if (p.equalsIgnoreCase("volcengine")) {
            ChatModel openAiChatModel = openAiChatModelProvider.getIfAvailable();
            if (openAiChatModel != null) {
                return openAiChatModel;
            }

            throw new IllegalStateException(
                    "当前 app.llm.provider=volcengine，但未创建 openAiChatModel。\n" +
                            "请检查火山引擎（OpenAI 兼容）配置是否生效：\n" +
                            "- spring.ai.openai.api-key\n" +
                            "- spring.ai.openai.base-url（示例：https://ark.cn-beijing.volces.com/api/v3）\n" +
                            "- spring.ai.openai.chat.options.model\n" +
                            "或改用环境变量：SPRING_AI_OPENAI_API_KEY / SPRING_AI_OPENAI_BASE_URL / SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL"
            );
        }

        ChatModel dashscopeChatModel = dashscopeChatModelProvider.getIfAvailable();
        if (dashscopeChatModel != null) {
            return dashscopeChatModel;
        }

        throw new IllegalStateException(
                "当前 app.llm.provider=" + p + "，但未创建 dashscopeChatModel。\n" +
                        "请检查配置：spring.ai.dashscope.api-key 或 spring.ai.dashscope.chat.api-key"
        );
    }
}
