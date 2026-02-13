package com.yupi.yuaiagent.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoveAppToolSelectionTest {

    static class StubToolCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;

        StubToolCallback(String name) {
            this.toolDefinition = ToolDefinition.builder()
                    .name(name)
                    .description("stub")
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return "";
        }
    }

    @Test
    void selectToolsForToolChat_shouldPreferDatePlanningToolsAndExcludeTerminate() {
        ToolCallback[] tools = new ToolCallback[]{
                new StubToolCallback("searchDateLocations"),
                new StubToolCallback("queryWeather"),
                new StubToolCallback("doTerminate"),
                new StubToolCallback("executeTerminalCommand")
        };

        ToolCallback[] selected = LoveApp.selectToolsForToolChat(tools, Set.of("dateLocation", "weather"));
        List<String> names = List.of(selected).stream()
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toList());

        assertEquals(2, names.size());
        assertTrue(names.contains("searchDateLocations"));
        assertTrue(names.contains("queryWeather"));
        assertFalse(names.contains("doTerminate"));
        assertFalse(names.contains("executeTerminalCommand"));
    }

    @Test
    void selectToolsForToolChat_shouldKeepOnlySafeToolsWhenNoSuggestionProvided() {
        ToolCallback[] tools = new ToolCallback[]{
                new StubToolCallback("searchDateLocations"),
                new StubToolCallback("queryWeather"),
                new StubToolCallback("doTerminate"),
                new StubToolCallback("readFile")
        };

        ToolCallback[] selected = LoveApp.selectToolsForToolChat(tools, Set.of());
        List<String> names = List.of(selected).stream()
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toList());

        assertEquals(2, names.size());
        assertTrue(names.contains("searchDateLocations"));
        assertTrue(names.contains("queryWeather"));
        assertFalse(names.contains("doTerminate"));
        assertFalse(names.contains("readFile"));
    }
}
