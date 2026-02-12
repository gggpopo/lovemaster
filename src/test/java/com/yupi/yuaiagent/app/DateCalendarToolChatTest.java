package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 通过 doChatWithTools 模拟用户输入，测试 DateCalendarTool 日期/纪念日工具
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
class DateCalendarToolChatTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void calculateAnniversary() {
        String chatId = UUID.randomUUID().toString();
        String message = "请调用工具 calculateAnniversary，参数 dateStr=2024-02-14。" +
                "只输出工具返回的结果，不要自行补充或编造。";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 计算恋爱纪念日 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void getUpcomingFestivals() {
        String chatId = UUID.randomUUID().toString();
        String message = "请调用工具 getUpcomingFestivals，参数 days=120。" +
                "只输出工具返回的结果，不要自行补充或编造。";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 查询未来节日 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }

    @Test
    void suggestDateByDate() {
        String chatId = UUID.randomUUID().toString();
        String message = "请调用工具 suggestDateByDate，参数 dateStr=2026-05-20。" +
                "只输出工具返回的结果，不要自行补充或编造。";
        String answer = loveApp.doChatWithTools(message, chatId);
        System.out.println("=== 分析指定日期是否适合约会 ===");
        System.out.println(answer);
        Assertions.assertNotNull(answer);
    }
}

