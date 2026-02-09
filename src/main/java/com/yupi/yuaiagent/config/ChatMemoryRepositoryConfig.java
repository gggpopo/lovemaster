package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.chatmemory.FileChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆仓库配置：启用后把消息持久化到本地文件。
 */
@Configuration
@ConditionalOnProperty(name = "app.memory.file.enabled", havingValue = "true", matchIfMissing = true)
public class ChatMemoryRepositoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(
            @Value("${app.memory.file.dir:${user.dir}/tmp/chat-memory}") String dir
    ) {
        return new FileChatMemoryRepository(dir);
    }
}

