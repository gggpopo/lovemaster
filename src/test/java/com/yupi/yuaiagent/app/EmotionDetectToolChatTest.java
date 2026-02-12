package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 模仿 LoveAppTest#doChatWithTools：让模型在工具列表中选择 EmotionDetectTool。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
class EmotionDetectToolChatTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void doChatWithTools() {
        testMessage("我今天太开心了，哈哈，和对象和好了，太好了！");
        testMessage("我们又吵架了，我好生气，真的很讨厌这种冷战。");
        testMessage("我有点焦虑，不知道该怎么办，感觉很担心。");
        testMessage("我不懂他是什么意思，为什么突然这样？我有点搞不懂。");
        testMessage("我好难过，感觉失恋了，想她想哭。");
    }

    private void testMessage(String userText) {
        String chatId = UUID.randomUUID().toString();
        String message = "请调用工具 detectEmotion 来分析下面这段用户消息的情绪：" + userText + "。" +
                "要求：只输出工具返回的 JSON 字符串，不要添加任何额外解释。";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== EmotionDetectTool ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
        Assertions.assertTrue(answer.contains("\"emotion\""));
    }
}

