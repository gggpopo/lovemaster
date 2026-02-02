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

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=LoveAppTest

# Run a single test method
mvn test -Dtest=LoveAppTest#testDoChat
```

### Server Details

- Backend runs on port `8123` with context path `/api`
- API docs: `http://localhost:8123/api/swagger-ui.html`
- Profiles: `local` (default), `prod`

## Architecture Overview

This is a Spring Boot 3.4.4 + Spring AI 1.0.0 project demonstrating AI application development with Java 21.

### Core Components

**LoveApp** (`src/main/java/com/yupi/yuaiagent/app/LoveApp.java`)
- AI chat application using Spring AI ChatClient
- Supports sync (`doChat`), streaming (`doChatByStream`), RAG (`doChatWithRag`), and tool-calling (`doChatWithTools`)
- Uses advisors: `MessageChatMemoryAdvisor`, `MyLoggerAdvisor`

**YuManus Agent** (`src/main/java/com/yupi/yuaiagent/agent/YuManus.java`)
- ReAct-style autonomous agent with planning capability
- Inheritance: `BaseAgent` -> `ReActAgent` -> `ToolCallAgent` -> `YuManus`
- Loop: `think()` (LLM decides tools) -> `act()` (execute tools) -> repeat until `TerminateTool` called

**REST Endpoints** (`src/main/java/com/yupi/yuaiagent/controller/AiController.java`)
- `/api/ai/love_app/chat/sync` - Synchronous chat
- `/api/ai/love_app/chat/sse` - SSE streaming chat
- `/api/manus/chat` - Agent execution with SSE streaming

### Tool System

Tools are registered in `src/main/java/com/yupi/yuaiagent/tools/ToolRegistration.java`:
- `FileOperationTool` - File I/O (writes to `./tmp/`)
- `WebSearchTool` - Web search via SearchAPI
- `WebScrapingTool` - HTML fetching/parsing
- `ResourceDownloadTool` - Download files from URLs
- `TerminalOperationTool` - Execute shell commands (Windows `cmd.exe`)
- `PDFGenerationTool` - Generate PDF documents
- `TerminateTool` - Signal agent completion

**Adding a new tool:**
1. Create class with `@Tool` annotated methods
2. Register in `ToolRegistration.allTools()`

### RAG Configuration

Two vector store options in `src/main/java/com/yupi/yuaiagent/rag/`:
- **In-memory** (default): `LoveAppVectorStoreConfig` loads from `classpath:document/*.md`
- **PgVector**: `PgVectorVectorStoreConfig` (commented out, requires DB setup)

### LLM Provider Selection

Configured via `app.llm.provider` in `application.yml`:
- `dashscope` (default) - Alibaba DashScope
- `volcengine` - Volcano Engine (OpenAI-compatible)

Selection logic in `src/main/java/com/yupi/yuaiagent/config/ChatModelSelectorConfig.java`

## Code Conventions

- **DI style**: Field injection with `@Resource`
- **Logging**: Lombok `@Slf4j`
- **Streaming**: `Flux<String>` for LoveApp SSE, `SseEmitter` for agent endpoints
- **Package structure**: `controller/`, `app/`, `agent/`, `tools/`, `rag/`, `advisor/`, `config/`

## Configuration Notes

- `DataSourceAutoConfiguration` is excluded in `YuAiAgentApplication` to allow running without a database
- PgVector and MCP client configs are commented out in `application.yml` for easier local setup
- Spring AI debug logging enabled: `org.springframework.ai: DEBUG`
