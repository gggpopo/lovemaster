package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向量长期记忆服务：将对话消息写入 VectorStore，并按语义检索召回。
 */
@Slf4j
@Service
public class VectorMemoryService {

    @Resource(name = "conversationMemoryVectorStore")
    private VectorStore vectorStore;

    @Value("${app.memory.vector.enabled:true}")
    private boolean vectorMemoryEnabled;

    @Value("${app.memory.vector.similarity-threshold:0.65}")
    private double similarityThreshold;

    public void saveMessages(String conversationId, List<Message> messages) {
        if (!vectorMemoryEnabled) {
            return;
        }
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (Message message : messages) {
            if (!(message instanceof UserMessage) && !(message instanceof AssistantMessage)) {
                continue;
            }
            String role = (message instanceof UserMessage) ? "用户" : "AI助手";
            String content = message.getText();
            if (!StringUtils.hasText(content)) {
                continue;
            }

            Document doc = new Document(role + ": " + content);
            Map<String, Object> md = doc.getMetadata();
            md.put("conversation_id", conversationId);
            md.put("message_type", message instanceof UserMessage ? "USER" : "ASSISTANT");
            md.put("timestamp", String.valueOf(System.currentTimeMillis()));
            md.put("memory_type", "conversation");
            documents.add(doc);
        }

        if (documents.isEmpty()) {
            return;
        }

        try {
            vectorStore.add(documents);
            log.debug("已存入 {} 条对话记忆到向量库, conversationId={}", documents.size(), conversationId);
        } catch (Exception e) {
            log.warn("写入向量记忆失败 conversationId={}", conversationId, e);
        }
    }

    /**
     * 检索与 query 语义相似的历史记忆，返回拼接后的文本。
     */
    public String retrieveRelevantMemories(String conversationId, String query, int topK) {
        if (!vectorMemoryEnabled) {
            return "";
        }
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(query) || topK <= 0) {
            return "";
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression("conversation_id == '" + escape(conversationId) + "' && memory_type == 'conversation'")
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            if (results == null || results.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (Document doc : results) {
                if (doc == null || !StringUtils.hasText(doc.getText())) {
                    continue;
                }
                sb.append(doc.getText()).append("\n");
            }
            String out = sb.toString().trim();
            log.debug("检索到 {} 条长期记忆, conversationId={}", results.size(), conversationId);
            return out;
        } catch (Exception e) {
            log.debug("检索长期记忆失败 conversationId={}", conversationId, e);
            return "";
        }
    }

    private String escape(String s) {
        // filterExpression 使用单引号，这里做最简单的转义
        return s.replace("'", "\\'");
    }
}
