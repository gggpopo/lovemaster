package com.yupi.yuaiagent.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 本地向量库 RAG 检索顾问配置
 */
@Configuration
@Slf4j
public class LoveAppRagAdvisorConfig {

    @Value("${app.rag.retriever.top-k:5}")
    private int topK;

    @Value("${app.rag.retriever.max-top-k:20}")
    private int maxTopK;

    @Value("${app.rag.retriever.similarity-threshold:0.55}")
    private double similarityThreshold;

    @Value("${app.rag.retriever.status-filter:}")
    private String statusFilter;

    @Bean(name = "loveAppRagAdvisor")
    public Advisor loveAppRagAdvisor(@Qualifier("loveAppVectorStore") VectorStore vectorStore) {
        int finalTopK = RagSettingUtil.normalizeTopK(topK, 5, maxTopK);
        double finalSimilarityThreshold = RagSettingUtil.normalizeSimilarityThreshold(similarityThreshold, 0.55d);

        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(finalTopK)
                .similarityThreshold(finalSimilarityThreshold);

        if (StrUtil.isNotBlank(statusFilter)) {
            Filter.Expression expression = new FilterExpressionBuilder().eq("status", statusFilter.trim()).build();
            retrieverBuilder.filterExpression(expression);
        }

        DocumentRetriever retriever = retrieverBuilder.build();
        log.info("[LoveAppRagAdvisorConfig-loveAppRagAdvisor] {}",
                kv("topK", finalTopK,
                        "similarityThreshold", finalSimilarityThreshold,
                        "statusFilter", StrUtil.blankToDefault(statusFilter, ""),
                        "vectorStore", vectorStore.getClass().getSimpleName()));

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}

