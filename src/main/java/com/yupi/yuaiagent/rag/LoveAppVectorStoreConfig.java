package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 恋爱大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Configuration
@Slf4j
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

    @Value("${app.rag.vectorstore.store:simple}")
    private String vectorStoreType;

    @Value("${app.rag.chunk.enabled:true}")
    private boolean chunkEnabled;

    @Value("${app.rag.vectorstore.pgvector.initialize-schema:false}")
    private boolean initializeSchema;

    @Value("${app.rag.vectorstore.pgvector.schema-name:public}")
    private String schemaName;

    @Value("${app.rag.vectorstore.pgvector.table-name:love_app_vector_store}")
    private String tableName;

    @Value("${app.rag.vectorstore.pgvector.dimensions:1536}")
    private int dimensions;

    @Value("${app.rag.vectorstore.pgvector.max-document-batch-size:10000}")
    private int maxDocumentBatchSize;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore loveAppVectorStore(@Qualifier("embeddingModel") EmbeddingModel embeddingModel,
                                   ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        String normalizedStoreType = RagSettingUtil.normalizeStoreType(vectorStoreType);
        VectorStore vectorStore = createVectorStore(normalizedStoreType, embeddingModel, jdbcTemplate);

        // 加载文档
        List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();
        log.info("[LoveAppVectorStoreConfig-loveAppVectorStore] {}",
                kv("storeType", normalizedStoreType,
                        "preloadDocuments", preloadDocuments,
                        "chunkEnabled", chunkEnabled,
                        "keywordEnrichEnabled", keywordEnrichEnabled,
                        "documentCount", documentList == null ? 0 : documentList.size()));

        // 测试/本地环境如果不想在启动时调用 Embedding，可关闭预加载
        if (!preloadDocuments) {
            return vectorStore;
        }

        // 自动补充关键词元信息（可选）
        List<Document> finalDocuments = documentList;
        if (chunkEnabled) {
            finalDocuments = myTokenTextSplitter.splitForRag(finalDocuments);
        }
        if (keywordEnrichEnabled) {
            finalDocuments = myKeywordEnricher.enrichDocuments(finalDocuments);
        }
        if (finalDocuments == null || finalDocuments.isEmpty()) {
            log.warn("[LoveAppVectorStoreConfig-loveAppVectorStore] {}",
                    kv("status", "skip_add_documents", "reason", "empty_documents_after_processing"));
            return vectorStore;
        }

        try {
            vectorStore.add(finalDocuments);
            log.info("[LoveAppVectorStoreConfig-loveAppVectorStore] {}",
                    kv("status", "documents_indexed",
                            "vectorStore", vectorStore.getClass().getSimpleName(),
                            "indexedCount", finalDocuments.size()));
        } catch (Exception e) {
            log.error("[LoveAppVectorStoreConfig-loveAppVectorStore] {}",
                    kv("status", "documents_index_failed",
                            "vectorStore", vectorStore.getClass().getSimpleName(),
                            "indexedCount", finalDocuments.size()), e);
        }
        return vectorStore;
    }

    private VectorStore createVectorStore(String normalizedStoreType,
                                          EmbeddingModel embeddingModel,
                                          JdbcTemplate jdbcTemplate) {
        if ("pgvector".equalsIgnoreCase(normalizedStoreType) && jdbcTemplate != null) {
            log.info("[LoveAppVectorStoreConfig-createVectorStore] {}",
                    kv("storeType", "pgvector",
                            "schemaName", schemaName,
                            "tableName", tableName,
                            "dimensions", dimensions,
                            "initializeSchema", initializeSchema));
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .dimensions(dimensions)
                    .distanceType(COSINE_DISTANCE)
                    .indexType(HNSW)
                    .initializeSchema(initializeSchema)
                    .schemaName(schemaName)
                    .vectorTableName(tableName)
                    .maxDocumentBatchSize(maxDocumentBatchSize)
                    .build();
        }
        if ("pgvector".equalsIgnoreCase(normalizedStoreType) && jdbcTemplate == null) {
            log.warn("[LoveAppVectorStoreConfig-createVectorStore] {}",
                    kv("storeType", "pgvector", "status", "fallback_to_simple", "reason", "jdbcTemplate_absent"));
        }
        log.info("[LoveAppVectorStoreConfig-createVectorStore] {}",
                kv("storeType", "simple"));
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
