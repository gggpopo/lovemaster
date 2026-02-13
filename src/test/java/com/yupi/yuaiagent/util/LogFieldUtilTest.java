package com.yupi.yuaiagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFieldUtilTest {

    @Test
    void shouldFormatKeyValuePairsInOrder() {
        String formatted = LogFieldUtil.kv(
                "chatId", "c1",
                "messageLength", 12,
                "imageCount", 2
        );

        assertEquals("chatId=c1, messageLength=12, imageCount=2", formatted);
    }

    @Test
    void shouldIgnoreDanglingKeyAndHandleNull() {
        String formatted = LogFieldUtil.kv(
                "traceId", null,
                "mode"
        );

        assertEquals("traceId=null", formatted);
    }
}
