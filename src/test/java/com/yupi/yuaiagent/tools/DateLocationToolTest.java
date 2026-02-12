package com.yupi.yuaiagent.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
class DateLocationToolTest {

    @Resource
    private DateLocationTool dateLocationTool;

    @Test
    void searchDateLocations() {
        // keywords, city, type
        String result = dateLocationTool.searchDateLocations("浪漫餐厅", "杭州", "restaurant");
        System.out.println(result);
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.contains("失败"));
    }

    @Test
    void searchDateLocationsWithEmptyCity() {
        String result = dateLocationTool.searchDateLocations("浪漫餐厅", "", "restaurant");
        Assertions.assertTrue(result.contains("请先提供城市名称"));
    }

    @Test
    void searchDateLocationsWithEmptyKeyword() {
        // keywords 为空时会降级为 type 或默认关键词，不应提示“请至少提供”
        String result = dateLocationTool.searchDateLocations("", "杭州", "restaurant");
        Assertions.assertNotNull(result);
    }
}
