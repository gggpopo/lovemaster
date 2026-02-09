# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
