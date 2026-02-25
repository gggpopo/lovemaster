package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredMidMemoryServiceTest {

    @Test
    void saveFromEvictedMessages_shouldExtractStructuredMemoriesAndSupportSearch() {
        StructuredMidMemoryService service = new StructuredMidMemoryService(null);
        String conversationId = "mem_struct_1";
        List<Message> evicted = List.of(
                new UserMessage("我喜欢安静的咖啡馆，预算300，纪念日是2024-02-14"),
                new AssistantMessage("可以考虑西湖附近环境安静的店。")
        );

        service.saveFromEvictedMessages(conversationId, evicted);
        List<MemoryCandidate> candidates = service.search(conversationId, "预算和纪念日", 5);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(c -> c.getContent().contains("预算")));
        assertTrue(candidates.stream().anyMatch(c -> c.getContent().contains("纪念日")));
    }
}
