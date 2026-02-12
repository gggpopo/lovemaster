package com.yupi.yuaiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EmotionDetectToolTest {

    @Resource
    private EmotionDetectTool emotionDetectTool;

    @Resource
    private ObjectMapper objectMapper;

    @Test
    void detectHappy() throws Exception {
        String json = emotionDetectTool.detectEmotion("我太开心了，哈哈，今天真的太好了！");
        JsonNode node = objectMapper.readTree(json);
        Assertions.assertEquals("HAPPY", node.path("emotion").asText());
        Assertions.assertTrue(node.path("confidence").asDouble() > 0);
        Assertions.assertTrue(node.path("suggestion").asText().contains("积极"));
    }

    @Test
    void detectSad() throws Exception {
        String json = emotionDetectTool.detectEmotion("我有点难过，感觉失恋了，想她想哭");
        JsonNode node = objectMapper.readTree(json);
        Assertions.assertEquals("SAD", node.path("emotion").asText());
    }

    @Test
    void detectNeutral() throws Exception {
        String json = emotionDetectTool.detectEmotion("你好，想咨询一下恋爱沟通的问题");
        JsonNode node = objectMapper.readTree(json);
        Assertions.assertTrue(node.path("emotion").asText().length() > 0);
        // 没有明显关键词时通常为 NEUTRAL
        Assertions.assertEquals("NEUTRAL", node.path("emotion").asText());
    }
}

