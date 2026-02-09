package com.yupi.yuaiagent.chatmemory.cloud;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(CloudMemoryProperties.class)
public class CloudMemoryConfig {

    @Bean("cloudMemoryExecutor")
    public Executor cloudMemoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cloud-memory-");
        executor.initialize();
        return executor;
    }
}

