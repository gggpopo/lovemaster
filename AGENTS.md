# yu-ai-agent — Architecture Overview

## Important Restrictions

- **DO NOT** run any `git` commands (`git commit`, `git push`, `git add`, `git checkout`, etc.). All version control is managed manually by the developer.
- **DO NOT** modify or delete existing files unless explicitly instructed. Prefer creating new files.
- **DO NOT** remove or comment out existing imports, beans, or configurations when adding new code.
- **DO NOT** run tests that require API keys unless explicitly asked. Most tests need `RUN_AI_TESTS=true`.
- **DO NOT** expose API keys, passwords, or secrets in code. Always use `${ENV_VAR}` or `@Value` injection.

## 1. Project Overview

This repo is a teaching project that demonstrates building AI applications and agents on **Java 21 + Spring Boot** with **Spring AI** (plus optional LangChain4j integrations).
这是一个项目的开发初期，并没有上线。

At a high level it contains three parts:

- **Backend service (`yu-ai-agent`)**: Spring Boot HTTP API that exposes:
  - an “AI 恋爱大师” chat app (`LoveApp`) with sync + streaming (SSE) endpoints.
  - a ReAct-style “超级智能体” (`YuManus`) that can plan, call tools, and stream step results.
- **Frontend (`yu-ai-agent-frontend/`)**: Vue 3 + Vite UI that talks to the backend via **SSE** (EventSource).
- **MCP server (`yu-image-search-mcp-server/`)**: a Spring AI MCP server providing an image search tool (Pexels) that can be run via SSE or stdio.

### Backend runtime flow (most common paths)

- HTTP entry points live in `src/main/java/com/yupi/yuaiagent/controller/AiController.java`.
  - `/api/ai/love_app/chat/sync` calls `LoveApp.doChat(...)`.
  - `/api/ai/love_app/chat/sse` streams `LoveApp.doChatByStream(...)` (Flux -> text/event-stream).
  - `/api/ai/manus/chat` creates a `YuManus` instance and streams step results via `SseEmitter`.
- `LoveApp` builds a `ChatClient` around a Spring AI `ChatModel` and uses advisors for:
  - chat memory (windowed in-memory by default)
  - request/response logging (`MyLoggerAdvisor`)
  - optional “re-reading” prompt augmentation (`ReReadingAdvisor`, currently commented in `LoveApp`)
- `YuManus` is a `ToolCallAgent` (ReAct loop):
  - `think()` calls the LLM with available tools and decides whether to call any.
  - `act()` executes tool calls via `ToolCallingManager` and appends tool results back to the message history.
  - `TerminateTool` sets the agent state to finished when invoked.

### Tooling and extensibility

- “Local tools” are plain Java classes with `@Tool` methods (Spring AI Tool Calling). They are instantiated and registered centrally in `src/main/java/com/yupi/yuaiagent/tools/ToolRegistration.java`.
- RAG support exists in two forms:
  - **In-memory vector store**: `LoveAppVectorStoreConfig` builds a `SimpleVectorStore` from markdowns under `classpath:document/*.md`.
  - **Cloud / PgVector options**: `LoveAppRagCloudAdvisorConfig` configures a DashScope document retriever; `PgVectorVectorStoreConfig` exists but is intentionally commented out for easier local dev.

## 2. Build & Commands

### Backend (`yu-ai-agent`)

- Build jar (as used by the root `Dockerfile`): `mvn clean package -DskipTests`
- Run production profile (as used by the root `Dockerfile`): `java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`

Notes:

- Default HTTP port is `8123` and context path is `/api` (`src/main/resources/application.yml`).
- PgVector and MCP client configuration blocks are present but commented out in `src/main/resources/application.yml` for easier local setup.

### Frontend (`yu-ai-agent-frontend/`)

- Install deps: `npm install`
- Dev server: `npm run dev` (Vite; default port `3000` in `yu-ai-agent-frontend/vite.config.js`)
- Build: `npm run build`

API routing behavior:

- Dev uses `http://localhost:8123/api` as base URL (`yu-ai-agent-frontend/src/api/index.js`).
- Prod uses relative `/api` and assumes reverse-proxying.
- The provided Docker Nginx config proxies `/api/` to `https://www.codefather.cn/api/` (`yu-ai-agent-frontend/nginx.conf`). If you deploy the backend yourself, update `proxy_pass` accordingly.

### MCP server (`yu-image-search-mcp-server/`)

- This module is a separate Spring Boot app.
- Default port is `8127` and default active profile is `sse` (`yu-image-search-mcp-server/src/main/resources/application.yml`).

Running modes:

- **SSE mode**: `spring.ai.mcp.server.stdio=false` (`application-sse.yml`)
- **stdio mode** (for MCP clients that spawn processes): `spring.ai.mcp.server.stdio=true` and `web-application-type=none` (`application-stdio.yml`)

The backend’s example stdio MCP configuration is in `src/main/resources/mcp-servers.json`.

## 3. Code Style

This section lists conventions that are enforced or consistently used in this repo (not general Java advice).

- **Package layout**: `com.yupi.yuaiagent.*` groups features by responsibility: `controller/`, `app/`, `agent/`, `tools/`, `rag/`, `advisor/`, `config/`.
- **Dependency injection**: Spring-managed components use `@Component/@Configuration` and field injection via `@Resource` (common throughout controllers and apps).
- **Lombok**: widely used for logging and boilerplate (`@Slf4j`, `@Data`, `@EqualsAndHashCode`).
- **Streaming**:
  - The love app uses Reactor `Flux<String>` for SSE (`MediaType.TEXT_EVENT_STREAM_VALUE`).
  - The agent endpoint uses `SseEmitter` and runs the ReAct loop in an async task (`CompletableFuture.runAsync`).
- **Adding a new tool**:
  - Implement a new class with one or more `@Tool` methods.
  - Register it in `ToolRegistration.allTools()` so both the love app tool-calling and `YuManus` can access it.

## 4. Testing

- Test framework: Spring Boot test starter (`spring-boot-starter-test` in `pom.xml`), using JUnit Jupiter (`@Test`).
- Convention: tests live under `src/test/java/**` and are named `*Test` / `*Tests`.
- Run tests: `mvn test`

Important repo-specific notes:

- Some tool tests assume a **Windows** shell command (e.g., `dir` in `src/test/java/com/yupi/yuaiagent/tools/TerminalOperationToolTest.java`). This matches the current `TerminalOperationTool` implementation which runs `cmd.exe`.

## 5. Security

Security-sensitive areas are concentrated around keys, tool-calling, and network access.

- **Do not commit secrets**: `src/main/resources/application-prod.yml` explicitly warns not to include sensitive information.
- **API keys are currently configured in plain text**:
  - `spring.ai.dashscope.api-key` and `search-api.api-key` are present in `src/main/resources/application.yml`.
  - `src/main/resources/mcp-servers.json` contains placeholder keys (e.g., `AMAP_MAPS_API_KEY`).
  - `src/main/java/com/yupi/yuaiagent/agent/model/ChatCompletionsVisionExample.java` contains a hard-coded `apiKey` value.
- **Tool-calling can execute side effects**:
  - `TerminalOperationTool` can execute arbitrary commands.
  - `FileOperationTool` and `ResourceDownloadTool` write to `System.getProperty("user.dir") + "/tmp"` (`FileConstant`).
  - `WebScrapingTool` fetches and returns raw HTML.
  Treat tool exposure as equivalent to granting the model those capabilities.
- **CORS is fully open**: `CorsConfig` allows `allowedOriginPatterns("*")` with credentials enabled.
- **Frontend reverse-proxy**: the provided `yu-ai-agent-frontend/nginx.conf` proxies `/api/` to an external domain; review before deploying.

## 6. Configuration

### Spring profiles

- Backend profile defaults to `local` (`spring.profiles.active: local` in `src/main/resources/application.yml`).
- Docker runs backend with `--spring.profiles.active=prod` (root `Dockerfile`).
- MCP server defaults to `sse` (`yu-image-search-mcp-server/src/main/resources/application.yml`).

### Key backend settings

- HTTP:
  - `server.port: 8123`
  - `server.servlet.context-path: /api`
- Spring AI (examples; values may be placeholders):
  - `spring.ai.dashscope.api-key` + `spring.ai.dashscope.chat.options.model`
  - `spring.ai.ollama.base-url` + `spring.ai.ollama.chat.model`
- API docs:
  - Springdoc Swagger UI path `/swagger-ui.html`
  - Knife4j enabled (`knife4j.enable: true`)
- Logging:
  - `org.springframework.ai: DEBUG` in `src/main/resources/application.yml` (useful when debugging prompts/tool calls).

### Optional integrations (currently commented in config)

- **PgVector**: datasource + vector store settings are commented in `src/main/resources/application.yml`, and `DataSourceAutoConfiguration` is excluded in `YuAiAgentApplication` to avoid requiring a DB in local dev.
- **MCP client**: an example MCP client configuration (SSE + stdio) is commented in `src/main/resources/application.yml`; stdio server definitions live in `src/main/resources/mcp-servers.json`.

# Logging Standards

## General Principles

- All critical business operations MUST have log statements. No business logic should run without logging ("no naked code").
- Log levels must strictly follow: TRACE < DEBUG < INFO < WARN < ERROR < FATAL.
- Production default log level is INFO. DEBUG/TRACE are only for local development and debugging.
- Direct output via `System.out.println` / `print()` / `console.log()` is forbidden. Always use a logging framework.

---

## Mandatory Logging Scenarios

### 1. API Entry and Exit

All externally exposed API endpoints MUST log request parameters at entry and response results at exit.

- Log Level: `INFO`
- Format Example:

```java
log.info("[API-Name] Request: {}", JSON.toJSONString(request));
log.info("[API-Name] Response: {}, Duration: {}ms", JSON.toJSONString(response), cost);
```

### 2. External Service Calls (RPC / HTTP / Third-Party API)

Log request parameters before the call and response results with duration after the call. Exceptions MUST be logged at ERROR level with the full stack trace.

- Log Level: `INFO` (normal) / `ERROR` (exception)
- Format Example:

```java
log.info("[Call-XXService] Request: {}", request);
log.info("[Call-XXService] Response: {}, Duration: {}ms", response, cost);
log.error("[Call-XXService] Exception, Request: {}", request, e);
```

### 3. Key State Changes of Core Entities / DTOs / BOs

Entity creation, update, and deletion operations MUST log the key fields before and after the change.

- Log Level: `INFO`
- Format Example:

```java
log.info("[Order-Create] orderId={}, userId={}, amount={}", order.getId(), order.getUserId(), order.getAmount());
log.info("[Status-Change] orderId={}, oldStatus={}, newStatus={}", orderId, oldStatus, newStatus);
```

### 4. Conditional Branches and Business Decision Points

When entering significant if/else or switch branches, log the conditions and the chosen path.

- Log Level: `INFO` or `DEBUG`
- Format Example:

```java
log.info("[Inventory-Check] skuId={}, currentStock={}, requiredQty={}, result={}", skuId, stock, required, sufficient ? "sufficient" : "insufficient");
```

### 5. Exception and Error Handling

All catch blocks MUST contain a log statement. Empty catch blocks are forbidden. Use WARN for business exceptions and ERROR for system exceptions. The exception object `e` MUST be included to preserve the full stack trace.

- Format Example:

```java
log.warn("[BizException] userId={}, reason: {}", userId, e.getMessage());
log.error("[SysException] method={}, params={}", methodName, params, e);
```

### 6. Scheduled Tasks / Async Tasks

Task start and completion MUST be logged, including the task name, data volume processed, and duration.

- Log Level: `INFO`
- Format Example:

```java
log.info("[ScheduledTask-XXX] Started");
log.info("[ScheduledTask-XXX] Completed, totalProcessed={}, success={}, failed={}, duration={}ms", total, success, fail, cost);
```

---

## Logging Rules

| Rule | Description |
|------|-------------|
| Use Placeholders | Use `{}` placeholders instead of string concatenation to avoid performance overhead |
| Data Masking | Sensitive fields such as phone numbers, ID cards, passwords, and tokens MUST be masked before logging |
| Avoid Large Objects | When a list exceeds 20 items, log only the size instead of the full content |
| TraceId Propagation | Ensure logs contain traceId / requestId for distributed tracing |
| No Logging in Loops | Avoid INFO-level logs inside loops. Use DEBUG with frequency control if needed |
| Safe toString | Ensure objects have a `toString()` method or use JSON serialization before logging to prevent NPE |

---

## Unified Log Format

- Use square brackets for prefix tags: `[ModuleName-OperationName]`
- Use `key=value` format for key fields to enable easy log searching
- Example:

```
[OrderService-create] orderId=123, userId=456, amount=99.00
```

---
