package com.yupi.yuaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // 仅用于让 Spring 上下文能启动；不应在测试里依赖真实密钥
        "spring.ai.dashscope.api-key=dummy",
        "spring.ai.openai.api-key=dummy",
        "search-api.api-key=dummy",
        // 避免启动阶段向量化文档而触发外部 Embedding 调用
        "app.rag.vectorstore.preload-documents=false"
})
class YuAiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
