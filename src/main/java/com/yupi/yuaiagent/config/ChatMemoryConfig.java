package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.chatmemory.ConversationSummaryService;
import com.yupi.yuaiagent.chatmemory.TieredChatMemoryAdvisor;
import com.yupi.yuaiagent.chatmemory.VectorMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 三层记忆架构装配：滑动窗口 ChatMemory + 摘要服务 + 向量长期记忆 + Advisor。
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ObjectProvider<ChatMemoryRepository> chatMemoryRepositoryProvider,
                                 @Value("${app.memory.window.max-messages:10}") int maxMessages) {
        ChatMemoryRepository chatMemoryRepository = chatMemoryRepositoryProvider.getIfAvailable();
        if (chatMemoryRepository == null) {
            chatMemoryRepository = new InMemoryChatMemoryRepository();
        }
        log.info("Init MessageWindowChatMemory, maxMessages={}", maxMessages);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * 用于“长期记忆”的向量存储（与 RAG 知识库向量存储分开，避免混在一起）。
     */
    @Bean(name = "conversationMemoryVectorStore")
    public VectorStore conversationMemoryVectorStore(
            @Qualifier("embeddingModel") EmbeddingModel embeddingModel,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${app.memory.vector.store:simple}") String storeType,
            @Value("${app.memory.vector.pgvector.initialize-schema:true}") boolean initializeSchema,
            @Value("${app.memory.vector.pgvector.schema-name:public}") String schemaName,
            @Value("${app.memory.vector.pgvector.table-name:conversation_memory_store}") String tableName,
            @Value("${app.memory.vector.pgvector.dimensions:1536}") int dimensions
    ) {
        String t = storeType == null ? "" : storeType.trim();
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();

        if ("pgvector".equalsIgnoreCase(t) && jdbcTemplate != null) {
            log.info("Init conversationMemoryVectorStore with PgVectorStore, schema={}, table={}, dimensions={}, initializeSchema={}",
                    schemaName, tableName, dimensions, initializeSchema);
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .dimensions(dimensions)
                    .distanceType(COSINE_DISTANCE)
                    .indexType(HNSW)
                    .initializeSchema(initializeSchema)
                    .schemaName(schemaName)
                    .vectorTableName(tableName)
                    .build();
        }

        log.info("Init conversationMemoryVectorStore with SimpleVectorStore (in-memory), storeType={}, jdbcTemplatePresent={}",
                t, jdbcTemplate != null);
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public TieredChatMemoryAdvisor tieredChatMemoryAdvisor(ChatMemory chatMemory,
                                                           ConversationSummaryService summaryService,
                                                           VectorMemoryService vectorMemoryService,
                                                           @Value("${app.memory.vector.topk:5}") int vectorTopK) {
        return TieredChatMemoryAdvisor.builder(chatMemory)
                .summaryService(summaryService)
                .vectorMemoryService(vectorMemoryService)
                .vectorTopK(vectorTopK)
                .build();
    }
}
