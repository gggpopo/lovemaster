package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * 恋爱大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Configuration
public class LoveAppVectorStoreConfig {

    /**
     * 是否在启动时使用大模型自动补充关键词元信息。
     *
     * 注意：开启后会在应用启动阶段调用 LLM（需要有效的 key），本地/测试环境建议关闭。
     */
    @Value("${app.rag.keyword-enrich.enabled:false}")
    private boolean keywordEnrichEnabled;

    /**
     * 是否在启动时预加载并向量化本地文档。
     *
     * 注意：预加载会在启动阶段调用 EmbeddingModel（需要有效的 key）。
     */
    @Value("${app.rag.vectorstore.preload-documents:false}")
    private boolean preloadDocuments;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore loveAppVectorStore(@Qualifier("embeddingModel") EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        // 加载文档
        List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();

        // 测试/本地环境如果不想在启动时调用 Embedding，可关闭预加载
        if (!preloadDocuments) {
            return simpleVectorStore;
        }

        // 自主切分文档
//        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);

        // 自动补充关键词元信息（可选）
        List<Document> finalDocuments = documentList;
        if (keywordEnrichEnabled) {
            finalDocuments = myKeywordEnricher.enrichDocuments(documentList);
        }

        simpleVectorStore.add(finalDocuments);
        return simpleVectorStore;
    }
}
