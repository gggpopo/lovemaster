package com.yupi.yuaiagent.orchestration.scene;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenePromptServiceTest {

    @Test
    void resolve_shouldUseRequestedSceneAndAdvanceStage() {
        ScenePromptService service = new ScenePromptService();
        String chatId = "scene_chat_1";

        SceneContext first = service.resolve(chatId, "first_date_planning", "我准备第一次约会，预算300");
        assertEquals("first_date_planning", first.getSceneId());
        assertEquals(SceneStage.DISCOVERY, first.getSceneStage());
        assertTrue(first.getPreferredTools().contains("dateLocation"));

        SceneContext second = service.resolve(chatId, null, "下一步我应该怎么做，给我具体步骤");
        assertEquals("first_date_planning", second.getSceneId());
        assertEquals(SceneStage.ACTION_PLAN, second.getSceneStage());
        assertEquals(2, second.getTurnCount());
    }

    @Test
    void resolve_shouldResetWhenSceneChanged() {
        ScenePromptService service = new ScenePromptService();
        String chatId = "scene_chat_2";

        service.resolve(chatId, "first_date_planning", "先聊聊约会");
        SceneContext switched = service.resolve(chatId, "cold_war_repair", "我们冷战了");

        assertEquals("cold_war_repair", switched.getSceneId());
        assertEquals(SceneStage.DISCOVERY, switched.getSceneStage());
        assertEquals(1, switched.getTurnCount());
    }

    @Test
    void mergeSuggestedTools_shouldIncludeScenePreferredTools() {
        ScenePromptService service = new ScenePromptService();
        SceneContext context = service.resolve("scene_chat_3", "first_date_planning", "推荐约会地点");

        Set<String> merged = service.mergeSuggestedTools(Set.of("webSearch"), context);
        assertTrue(merged.contains("webSearch"));
        assertTrue(merged.contains("dateLocation"));
    }
}
