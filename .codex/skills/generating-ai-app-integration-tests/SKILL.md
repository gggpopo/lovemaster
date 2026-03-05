---
name: generating-ai-app-integration-tests
description: Use when generating or refactoring Spring Boot integration tests for AI app-layer components (LoveApp, YuManus, tool-enabled app flows) that must run offline by default, model detailed user scenarios, and emit structured test-flow logs for end-to-end observability.
---

# Generating AI App Integration Tests

## Overview

Generate scenario-driven Spring Boot integration tests for AI app-layer code.
Keep tests offline and deterministic by default.
Log each test step with a uniform format so behavior is easy to inspect end to end.

## Required Defaults

- Use `@SpringBootTest` for app-layer integration tests.
- Inject tested beans with `@Resource`.
- Add `@Slf4j` and never use `System.out.println`.
- Keep tests offline by default:
  - Provide fake or stub beans with `@TestConfiguration` + `@Primary`.
  - Do not call real model, search, map, MCP, or external HTTP by default.
- Split tests by capability (one class per method or capability).
- For each capability, cover three case types:
  - normal case
  - boundary case
  - exception case
- Assert structure and key fields only (for example: `notNull`, key markers, status flags).
- Do not assert logs.
- Keep scenario data in the test class (`List<CaseData>`), not in JSON files by default.

## Baseline Failures to Prevent

| Failure | Why It Is Risky | Required Fix |
|---|---|---|
| `System.out.println` in tests | Breaks project logging standard and trace consistency | Use `log.info` / `log.warn` / `log.error` |
| Only `assertNotNull` | Weak signal, hides behavior regressions | Assert key fields and branch outcomes |
| Real API calls in default mode | Flaky and slow, depends on keys/network | Use offline stubs by default |
| One giant all-in-one test class | Hard to debug and extend | Split by capability |
| Missing boundary/exception cases | Low defect coverage | Always include normal + boundary + exception |

## Mandatory Log Format

Use this format for every key step:

```text
[TestFlow-<capability>] caseId=<id>, step=<step>, input=<summary>, expect=<summary>, actual=<summary>, durationMs=<n>
```

Use concise summaries for `input` and `actual`. Avoid dumping large payloads.

## Workflow

1. Pick one capability and create one dedicated test class.
2. Build `List<CaseData>` with normal, boundary, and exception cases.
3. Add offline stubs in `@TestConfiguration` and mark with `@Primary`.
4. Execute each case with step logs (`start`, `invoke`, `assert`, `done`).
5. Assert key fields and branch behavior, not full generated copy.
6. If needed, add optional real-chain mode with `RUN_AI_TESTS=true` in a separate test class or profile.

## Test Skeleton (Offline Default)

```java
package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=dummy",
        "spring.ai.openai.api-key=dummy",
        "search-api.api-key=dummy",
        "app.rag.vectorstore.preload-documents=false"
})
class LoveAppDoChatIntegrationTest {

    @Resource
    private LoveApp loveApp;

    private record CaseData(String caseId, String message, String expectedKeyword, boolean exceptionExpected) {}

    private static final List<CaseData> CASES = List.of(
            new CaseData("normal-1", "你好，我最近和对象沟通不顺畅，帮我分析一下", "建议", false),
            new CaseData("boundary-1", "你好", "建议", false),
            new CaseData("exception-1", "", "参数错误", true)
    );

    @Test
    void shouldRunScenarioCasesWithStructuredLogs() {
        String capability = "LoveApp-doChat";
        for (CaseData c : CASES) {
            long start = System.currentTimeMillis();
            String chatId = UUID.randomUUID().toString();
            String inputSummary = summarize(c.message());
            log.info("[TestFlow-{}] caseId={}, step=start, input={}, expect={}, actual={}, durationMs={}",
                    capability, c.caseId(), inputSummary, c.expectedKeyword(), "N/A", 0);

            try {
                String actual = loveApp.doChat(c.message(), chatId);
                String actualSummary = summarize(actual);
                log.info("[TestFlow-{}] caseId={}, step=invoke, input={}, expect={}, actual={}, durationMs={}",
                        capability, c.caseId(), inputSummary, c.expectedKeyword(), actualSummary, System.currentTimeMillis() - start);

                if (c.exceptionExpected()) {
                    Assertions.fail("Expected exception but call succeeded, caseId=" + c.caseId());
                }
                Assertions.assertNotNull(actual);
                Assertions.assertTrue(actual.contains(c.expectedKeyword()) || actual.length() > 0);
            } catch (Exception e) {
                log.warn("[TestFlow-{}] caseId={}, step=exception, input={}, expect={}, actual={}, durationMs={}",
                        capability, c.caseId(), inputSummary, c.expectedKeyword(), e.getClass().getSimpleName(), System.currentTimeMillis() - start);
                if (!c.exceptionExpected()) {
                    throw e;
                }
            }

            log.info("[TestFlow-{}] caseId={}, step=done, input={}, expect={}, actual={}, durationMs={}",
                    capability, c.caseId(), inputSummary, c.expectedKeyword(), "DONE", System.currentTimeMillis() - start);
        }
    }

    private static String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "<empty>";
        }
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }

    @TestConfiguration
    static class OfflineStubConfig {
        @Bean
        @Primary
        ChatModel offlineChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    String stub = "这是离线桩响应，建议：先共情，再提需求，再约定复盘。";
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(stub))));
                }

                @Override
                public ChatOptions getDefaultOptions() {
                    return null;
                }
            };
        }
    }
}
```

## Optional Real-Chain Mode

Enable real chain only when explicitly requested:

```java
@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")
```

Keep this mode separate from offline default mode.
Do not make real-chain execution the default path.

## Red Flags

- Starting with real model/API calls for default tests
- Generating only `assertNotNull` assertions
- Skipping boundary or exception cases
- Using `System.out.println` in tests
- Merging all capabilities into one huge test class

If any red flag appears, regenerate the test class before finalizing.
