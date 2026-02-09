package com.yupi.yuaiagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 统一选择项目中使用的 EmbeddingModel。
 * <p>
 * - 默认使用 DashScope（阿里云百练）
 * - 配置 app.llm.provider=volcengine 时，优先使用 OpenAI 兼容的 Embedding 模型（用于火山引擎 Ark / 豆包）
 */
@Configuration
public class EmbeddingModelSelectorConfig {

    /**
     * @param provider dashscope / volcengine
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Qualifier("dashscopeEmbeddingModel") ObjectProvider<EmbeddingModel> dashscopeEmbeddingModelProvider,
            @Qualifier("openAiEmbeddingModel") ObjectProvider<EmbeddingModel> openAiEmbeddingModelProvider,
            @Value("${app.llm.provider:dashscope}") String provider
    ) {
        String p = provider == null ? "" : provider.trim();

        if (p.equalsIgnoreCase("volcengine")) {
            EmbeddingModel openAiEmbeddingModel = openAiEmbeddingModelProvider.getIfAvailable();
            if (openAiEmbeddingModel != null) {
                return openAiEmbeddingModel;
            }

            throw new IllegalStateException(
                    "当前 app.llm.provider=volcengine，但未创建 openAiEmbeddingModel。\n" +
                            "请检查火山引擎（OpenAI 兼容）Embedding 配置是否生效：\n" +
                            "- spring.ai.openai.api-key\n" +
                            "- spring.ai.openai.base-url（示例：https://ark.cn-beijing.volces.com/api/v3）\n" +
                            "- spring.ai.openai.embedding.options.model（示例：doubao-embedding）\n" +
                            "或改用环境变量：SPRING_AI_OPENAI_API_KEY / SPRING_AI_OPENAI_BASE_URL / SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL"
            );
        }

        EmbeddingModel dashscopeEmbeddingModel = dashscopeEmbeddingModelProvider.getIfAvailable();
        if (dashscopeEmbeddingModel != null) {
            return dashscopeEmbeddingModel;
        }

        throw new IllegalStateException(
                "当前 app.llm.provider=" + p + "，但未创建 dashscopeEmbeddingModel。\n" +
                        "请检查配置：spring.ai.dashscope.api-key"
        );
    }
}
