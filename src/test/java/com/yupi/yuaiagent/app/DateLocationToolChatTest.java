package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 通过 doChatWithTools 模拟用户输入，测试 DateLocationTool 约会地点推荐
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
class DateLocationToolChatTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void searchDateLocationByCity() {
        // 模拟用户输入：指定城市搜索约会餐厅
        String chatId = UUID.randomUUID().toString();
        String message = "帮我在杭州找几家适合约会的浪漫餐厅,并提供图片";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 搜索杭州约会餐厅 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void searchCoffeeShop() {
        // 模拟用户输入：搜索安静的咖啡厅
        String chatId = UUID.randomUUID().toString();
        String message = "我想在上海找一家安静的咖啡厅和女朋友聊聊天，有推荐吗？";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 搜索上海安静咖啡厅 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void searchParkForDate() {
        // 模拟用户输入：搜索适合散步的公园
        String chatId = UUID.randomUUID().toString();
        String message = "周末想带对象去北京逛公园，推荐几个适合情侣散步的公园吧";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 搜索北京约会公园 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void searchMovieTheater() {
        // 模拟用户输入：搜索电影院
        String chatId = UUID.randomUUID().toString();
        String message = "我在成都，想找个评分高的电影院看电影约会，帮我搜搜";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 搜索成都电影院 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void multiTurnDatePlanning() {
        // 模拟多轮对话：先搜地点，再追问细节
        String chatId = UUID.randomUUID().toString();
        String answer1 = loveApp.doChatWithTools(
                "我在深圳，七夕想带女朋友去吃饭，帮我找几家浪漫的餐厅", chatId);
        System.out.println("=== 第一轮：搜索深圳浪漫餐厅 ===");
        System.out.println(answer1);
        Assertions.assertNotNull(answer1);

        String answer2 = loveApp.doChatWithTools(
                "吃完饭还想去附近逛逛，帮我再找几个适合饭后散步的商场或公园", chatId);
        System.out.println("=== 第二轮：追问饭后去处 ===");
        System.out.println(answer2);
        Assertions.assertNotNull(answer2);
    }
}
