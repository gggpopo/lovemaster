package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyTokenTextSplitterTest {

    @Test
    void shouldSplitLongDocumentForRag() {
        MyTokenTextSplitter splitter = new MyTokenTextSplitter();
        ReflectionTestUtils.setField(splitter, "maxTokens", 40);
        ReflectionTestUtils.setField(splitter, "minChunkSizeChars", 10);
        ReflectionTestUtils.setField(splitter, "minChunkLengthToEmbed", 5);
        ReflectionTestUtils.setField(splitter, "maxNumChunks", 500);
        ReflectionTestUtils.setField(splitter, "keepSeparator", true);
        String longText = "恋爱关系沟通建议 ".repeat(300);

        List<Document> result = splitter.splitForRag(List.of(new Document(longText)));

        assertTrue(result.size() > 1);
    }

    @Test
    void shouldReturnEmptyWhenInputEmpty() {
        MyTokenTextSplitter splitter = new MyTokenTextSplitter();
        List<Document> result = splitter.splitForRag(List.of());
        assertEquals(0, result.size());
    }
}

