package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 通过 doChatWithTools 模拟用户输入，测试 WeatherTool 天气查询
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
class WeatherToolChatTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void queryWeatherForDate() {
        String chatId = UUID.randomUUID().toString();
        String message = "我周末想在杭州约会，帮我看看杭州这几天天气怎么样";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 查询杭州天气 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void queryWeatherRainyAdvice() {
        String chatId = UUID.randomUUID().toString();
        String message = "明天想带女朋友出去玩，先帮我查查上海的天气，看看适不适合户外活动";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 上海天气与户外建议 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void weatherAndLocationCombo() {
        // 多轮对话：先查天气，再根据天气推荐地点
        String chatId = UUID.randomUUID().toString();
        String answer1 = loveApp.doChatWithTools(
                "帮我查一下北京这几天的天气", chatId);
        System.out.println("=== 第一轮：查北京天气 ===");
        System.out.println(answer1);
        Assertions.assertNotNull(answer1);

        String answer2 = loveApp.doChatWithTools(
                "根据天气情况，帮我推荐几个适合约会的地方", chatId);
        System.out.println("=== 第二轮：根据天气推荐约会地点 ===");
        System.out.println(answer2);
        Assertions.assertNotNull(answer2);
    }
}
