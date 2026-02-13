package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 自定义基于 Token 的切词器
 */
@Component
@Slf4j
class MyTokenTextSplitter {

    @Value("${app.rag.chunk.max-tokens:300}")
    private int maxTokens = 300;

    @Value("${app.rag.chunk.min-chars:80}")
    private int minChunkSizeChars = 80;

    @Value("${app.rag.chunk.min-words:20}")
    private int minChunkLengthToEmbed = 20;

    @Value("${app.rag.chunk.max-chunks:2000}")
    private int maxNumChunks = 2000;

    @Value("${app.rag.chunk.keep-separator:true}")
    private boolean keepSeparator = true;

    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        return splitter.apply(documents);
    }

    public List<Document> splitForRag(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        TokenTextSplitter splitter = new TokenTextSplitter(
                maxTokens,
                minChunkSizeChars,
                minChunkLengthToEmbed,
                maxNumChunks,
                keepSeparator
        );
        List<Document> splitDocuments = splitter.apply(documents);
        log.info("[MyTokenTextSplitter-splitForRag] {}",
                kv("inputCount", documents.size(),
                        "outputCount", splitDocuments == null ? 0 : splitDocuments.size(),
                        "maxTokens", maxTokens,
                        "minChunkSizeChars", minChunkSizeChars,
                        "minChunkLengthToEmbed", minChunkLengthToEmbed,
                        "maxNumChunks", maxNumChunks,
                        "keepSeparator", keepSeparator));
        return splitDocuments;
    }
}
