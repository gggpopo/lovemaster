# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
## Important Restrictions

- **DO NOT** run any `git` commands (`git commit`, `git push`, `git add`, `git checkout`, etc.). All version control is managed manually by the developer.
- **DO NOT** modify or delete existing files unless explicitly instructed. Prefer creating new files.
- **DO NOT** remove or comment out existing imports, beans, or configurations when adding new code.
- **DO NOT** run tests that require API keys unless explicitly asked. Most tests need `RUN_AI_TESTS=true`.
- **DO NOT** expose API keys, passwords, or secrets in code. Always use `${ENV_VAR}` or `@Value` injection.

## Build & Run Commands

### Backend (Maven)

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Run with local profile (default)
mvn spring-boot:run

# Run with production profile
java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Run all tests (requires API keys configured)
RUN_AI_TESTS=true mvn test

# Run a single test class
RUN_AI_TESTS=true mvn test -Dtest=LoveAppTest

# Run a single test method
RUN_AI_TESTS=true mvn test -Dtest=LoveAppTest#testDoChat
```

**Test gating**: AI-dependent tests use `@EnabledIfEnvironmentVariable(named = "RUN_AI_TESTS", matches = "true")`. Without this env var, most tests are skipped to avoid API calls.

### Frontend (`yu-ai-agent-frontend/`)

```bash
npm install
npm run dev    # Vite dev server
npm run build  # Production build
```

Dev mode hits `http://localhost:8123/api`; prod uses relative `/api` (expects reverse proxy).

### Server Details

- Backend runs on port `8123` with context path `/api`
- API docs: `http://localhost:8123/api/swagger-ui.html`
- Profiles: `local` (default), `prod`

## Architecture Overview

Spring Boot 3.4.4 + Spring AI 1.0.0 project with Java 21. Three modules:

1. **Backend** (`yu-ai-agent`) — Spring Boot API with AI chat app and autonomous agent
2. **Frontend** (`yu-ai-agent-frontend/`) — Vue 3 + Vite UI communicating via SSE
3. **MCP Server** (`yu-image-search-mcp-server/`) — Separate Spring Boot app providing image search tool (Pexels API), runs on port `8127`

### Core Components

**LoveApp** (`src/main/java/com/yupi/yuaiagent/app/LoveApp.java`)
- AI chat application using Spring AI ChatClient
- Modes: sync (`doChat`), streaming (`doChatByStream`), RAG (`doChatWithRag`), tool-calling (`doChatWithTools`), vision (`doChatWithVision`)
- Uses advisors: `MessageChatMemoryAdvisor`, `MyLoggerAdvisor`
- Stream termination marker: `[DONE]`

**YuManus Agent** (`src/main/java/com/yupi/yuaiagent/agent/YuManus.java`)
- ReAct-style autonomous agent (max 20 steps)
- Inheritance: `BaseAgent` → `ReActAgent` → `ToolCallAgent` → `YuManus`
- Loop: `think()` (LLM decides tools) → `act()` (execute via `ToolCallingManager`) → repeat until `TerminateTool` called
- `ToolCallAgent` disables Spring AI's internal tool execution (`chatOptions`) and manages tool calls manually

**REST Endpoints** (`src/main/java/com/yupi/yuaiagent/controller/AiController.java`)
- `GET /api/ai/love_app/chat/sync` — Synchronous chat (params: `message`, `chatId`)
- `GET /api/ai/love_app/chat/sse` — SSE streaming via `Flux<String>`
- `GET /api/ai/love_app/chat/server_sent_event` — SSE wrapped in `ServerSentEvent<String>`
- `GET /api/ai/love_app/chat/sse_emitter` — SSE via `SseEmitter` (3-min timeout)
- `POST /api/ai/love_app/chat/vision` — Vision chat with Base64 images (`VisionChatRequest` body)
- `GET /api/ai/manus/chat` — Agent execution with SSE streaming

### Agent Architecture

`BaseAgent` defines the execution loop (`run`/`runStream` with `maxSteps` guard and `AgentState` enum: IDLE → RUNNING → FINISHED/ERROR). `ReActAgent` splits each step into `think()` + `act()`. `ToolCallAgent` implements these: `think()` calls the LLM with available tools and parses `AssistantMessage` for tool call decisions; `act()` executes them and detects `TerminateTool` to set FINISHED state.

### Tool System

Tools registered in `src/main/java/com/yupi/yuaiagent/tools/ToolRegistration.java`:

**Core tools** (always enabled):
- `FileOperationTool` — File I/O (writes to `${user.dir}/tmp/file`)
- `WebSearchTool` — Web search via SearchAPI
- `WebScrapingTool` — HTML fetching/parsing (Jsoup)
- `ResourceDownloadTool` — Download files from URLs
- `TerminalOperationTool` — Execute shell commands (currently Windows `cmd.exe`)
- `PDFGenerationTool` — Generate PDF documents (iText)
- `TerminateTool` — Signal agent completion

**Optional tools** (toggled via `app.tools.*` config):
- `StickerRecommendTool` (`app.tools.sticker.enabled`)
- `ContentSafetyTool` (`app.tools.content-safety.enabled`)
- `ToneStyleTool` (`app.tools.tone-style.enabled`)

**Adding a new tool:**
1. Create class with `@Tool` annotated methods (use `@ToolParam` for parameter descriptions)
2. Register in `ToolRegistration.allTools()`

### Chat Memory Architecture

Three-tier system in `chatmemory/`:
- `TieredChatMemoryRepository` — Local memory → Redis → File persistence
- File-based memory enabled by default (`app.memory.file.enabled: true`, stores in `./tmp/chat-memory`)
- Redis and cloud memory disabled by default
- Configuration in `ChatMemoryRepositoryConfig`

### Multi-tenancy

Optional system in `tenant/` (disabled by default via `app.tenant.enabled`):
- `TenantInterceptor` extracts tenant/user IDs from headers (`X-Tenant-Id`, `X-User-Id`) or query params
- `TenantContext` uses ThreadLocal for request-scoped tenant state
- `RateLimitAdvisor` provides per-tenant rate limiting (60 req/min)

### LLM Provider Selection

Configured via `app.llm.provider` in `application.yml`:
- `dashscope` — Alibaba DashScope (model: `qwen-plus`)
- `volcengine` — Volcano Engine/Doubao (OpenAI-compatible, custom base URL)

Selection logic in `src/main/java/com/yupi/yuaiagent/config/ChatModelSelectorConfig.java`

### RAG Configuration

Two vector store options in `src/main/java/com/yupi/yuaiagent/rag/`:
- **In-memory** (default): `LoveAppVectorStoreConfig` loads from `classpath:document/*.md`
- **PgVector**: `PgVectorVectorStoreConfig` (commented out, requires DB setup)
- RAG document preloading disabled by default (`app.rag.preload-documents: false`) to avoid embedding API calls on startup

## Code Conventions

- **DI style**: Field injection with `@Resource` (not `@Autowired`)
- **Logging**: Lombok `@Slf4j`
- **Streaming**: `Flux<String>` for LoveApp SSE, `SseEmitter` + `CompletableFuture.runAsync` for agent endpoints
- **Package structure**: `controller/`, `app/`, `agent/`, `tools/`, `rag/`, `advisor/`, `config/`, `chatmemory/`, `tenant/`

## Configuration Notes

- `DataSourceAutoConfiguration` is excluded in `YuAiAgentApplication` to allow running without a database
- PgVector and MCP client configs are commented out in `application.yml` for easier local setup
- Spring AI debug logging enabled: `org.springframework.ai: DEBUG`
- `spring-ai-core` is excluded from OpenAI and Ollama starters in `pom.xml` to avoid class conflicts with Spring AI 1.0.0 modular dependencies — be aware of this when adding new Spring AI starters
- CORS is fully open (`CorsConfig` allows all origins with credentials)
- Tool file storage base path: `FileConstant.FILE_SAVE_DIR` = `${user.dir}/tmp`
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