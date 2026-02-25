# 猫箱记忆系统：基于 Spring AI 的工程化落地指南

本文档基于对“猫箱”情感陪伴型 Agent 记忆系统的分析，旨在提供一份面向工程实践的技术方案。方案以 Java 和 Spring AI 技术栈为核心，对记忆系统的关键设计、可复用框架及具体实现逻辑进行详细阐述，为其他情感类 Agent 的开发提供参考。

## 1. 猫箱记忆设计关键

“猫箱”记忆系统的核心，是在工程可行性与理想效果之间寻求平衡，通过分层、增量和混合检索的策略，构建一套成本可控、体验连贯的记忆机制。

- **分层增量摘要**：系统并未试图无限存储原始对话，而是将记忆分层。**中期记忆**（经历的事件）通过对近期对话的准实时增量摘要生成，捕捉短期交互的动态；**长期记忆**（Persona 演化）则是在业务低峰期，通过对中期记忆的再处理进行沉淀和升华，形成更稳固的用户画像与 Agent 人设。这一机制有效压缩了信息，降低了存储和检索成本。

- **混合召回策略**：记忆的唤醒并非单一路径。系统采用了“被动”与“主动”相结合的混合召回模式。**被动召回**将最核心的长期记忆（如用户画像）和部分高价值中期记忆固定拼接到每次对话的上下文中，保证基础人设的连贯性。**主动召回**则利用大型语言模型（LLM）的工具调用（Function Calling）能力，在模型判断需要额外信息时，触发一个 RAG (Retrieval-Augmented Generation) 流程，从向量库中精准检索相关记忆片段，以回答关于特定历史细节的问题。

- **个性化与反馈加权**：记忆系统并非静态的数据库，它具备一定的动态调整能力。通过引入用户的偏好信号，例如对某条记忆的高亮、点赞或删除操作，系统可以调整特定记忆片段在生成与召回过程中的权重。这为未来的个性化排序和实现“选择性遗忘”提供了基础，使记忆更贴近用户的真实感受。

## 2. 可复用的记忆框架与实现逻辑

“猫箱”的记忆设计思路可以被抽象为一个通用的“记忆金字塔”模型和一套标准的处理管线，这为其他情感类、助理类 Agent 的记忆系统实现提供了可复用的框架。

### 记忆金字塔模型

该模型将 Agent 的记忆体系从下至上分为四个层次，信息密度和稳定性逐层递增：

- **短期记忆（Short-Term Memory）**：即时对话的上下文窗口。它完全易失，只存在于单次 LLM 请求中，由模型自身的注意力机制管理。
    - **可复用性**：通用概念，所有基于 LLM 的 Agent 都有。
    - **定制化**：窗口大小（Token 限制）需根据模型能力和成本考量进行配置。

- **中期记忆（Mid-Term Memory）**：结构化的、可检索的近期事件摘要。它是将原始对话流转化为结构化知识的第一站，例如“用户上周提到了想去旅游”。
    - **可复用性**：增量摘要的生成机制、基于时间/轮数的触发逻辑可直接复用。
    - **定制化**：摘要的内容模式（Schema），如“事件”、“问答”、“观点”等，需根据 Agent 的业务场景进行定制。

- **长期记忆（Long-Term Memory）**：高度凝练的、稳定的知识沉淀。它通常由中期记忆在离线状态下经过进一步的抽象、合并和去重生成，例如“用户是一个喜欢旅游和摄影的人”。
    - **可复用性**：离线批处理的调度逻辑、从中期到长期的整合 Prompt 范式可复用。
    - **定制化**：长期记忆的维度（如用户画像、Agent 人设、关系里程碑）需要针对具体业务场景精心设计。

- **Persona / 世界知识（Persona / World Knowledge）**：最顶层的、近乎静态的核心设定或外部知识。它包括 Agent 的基础人设、用户的核心档案（如姓名、生日，需用户授权），或是一些外部引入的领域知识库。
    - **可复用性**：作为兜底上下文的拼接逻辑可复用。
    - **定制化**：Persona 的具体内容、外部知识库的来源和格式需完全定制。

### 记忆处理管线 (Memory Pipeline)

围绕“记忆金字塔”的构建与使用，可以定义一套标准的五阶段处理管线：

1.  **摄取 (Ingestion)**：接收原始信息流，主要是用户与 Agent 的对话历史。
2.  **摘要 (Summarization)**：通过 LLM 将非结构化的对话历史，转化为结构化的中期记忆（事件、画像片段）。
3.  **存储 (Storage)**：将结构化记忆及其向量表示（Embedding）存入记忆库（通常是关系型数据库 + 向量数据库的组合）。
4.  **检索 (Retrieval)**：在需要时，通过混合策略（被动拼接 + 主动 RAG）从记忆库中召回相关记忆。
5.  **治理 (Governance)**：对记忆库进行长期维护，包括去重、压缩、遗忘（TTL/软删除）、基于用户反馈的权重调整、以及隐私合规处理。

这套管线定义了记忆生命周期的完整闭环。对于任何新的情感类 Agent，开发者都可以基于这个框架，重点投入 **“摘要”** 和 **“检索”** 两个阶段的 Prompt Engineering 和策略优化，并根据业务需求定制 **“治理”** 策略，从而快速搭建起一套有效的记忆系统。

## 3. 技术方案 (Java + Spring AI)

本章节将详细阐述如何使用 Java 和 Spring AI 生态，将上述记忆框架落地为具体的工程实现。

### 3.1. 架构分层与组件

一个典型的基于 Spring AI 的记忆系统可以划分为以下几个核心组件：

![架构图](https://p.ipic.vip/fzv5x5.png)

- **ChatClient / ChatModel**: Spring AI 提供的与大型语言模型（如 OpenAI GPT、Anthropic Claude）交互的核心客户端。它负责发送请求、接收回复，并且是执行工具调用（Function Calling）的入口。[[716]](https://howtodoinjava.com/spring-ai/chatbot-example-with-rag/)
- **EmbeddingClient / EmbeddingModel**: 负责将文本（如记忆内容、用户查询）转换为向量表示（Embeddings）的客户端。Spring AI 对接了多种 Embedding 模型提供商。[[713]](https://zilliz.com/blog/spring-ai-and-milvus-using-milvus-as-spring-ai-vector-store)
- **VectorStore**: 向量存储与检索引擎的统一抽象接口。Spring AI 提供了对多种主流向量数据库的开箱即用支持，如 PGVector, Milvus, Qdrant, Redis 等，极大地简化了向量的存取操作。[[714]](https://github.com/spring-projects/spring-ai)
- **MemoryStore (自定义)**: 我们需要自行设计的组件，负责管理非向量化的记忆数据和元数据。通常可以使用 RDBMS (如 MySQL/PostgreSQL) 实现，存储记忆的结构化文本、ID、时间戳、用户关联等信息。
- **RAG Orchestrator (自定义)**: 负责编排整个 RAG 流程的服务。它响应 `ChatClient` 的工具调用请求，从 `VectorStore` 和 `MemoryStore` 中检索信息，并将其格式化后返回给 `ChatClient`。
- **SessionStore (自定义/框架集成)**: 用于管理多轮对话的会话历史（短期记忆）。虽然 Spring AI 提供了 `ChatMemory` 抽象，但在分布式环境中，通常需要结合 Redis 或数据库来实现一个可扩展的会话存储方案。
- **异步任务/调度 (Async Tasks)**: 基于 Spring 的 `@Async` 和 `@Scheduled` 注解，或结合消息队列（如 RabbitMQ/Kafka）实现。负责执行耗时的记忆生成任务，如中期记忆的增量摘要和长期记忆的离线沉淀，避免阻塞主对话流程。
- **监控与评估 (Monitoring & Evaluation)**: 对接 Prometheus/Grafana 等监控系统，收集记忆系统的关键指标（如召回率、时延、LLM 调用成本）。同时，建立评估数据集和流程，持续度量记忆质量。

### 3.2. 数据模型与索引

记忆系统的核心是 `MemoryRecord` 数据模型，其设计直接影响到检索的灵活性和效率。

**数据模型 (`MemoryRecord.java`)**:

```java
public class MemoryRecord {
    private UUID id;            // 唯一标识
    private String userId;        // 关联的用户 ID
    private String sessionId;     // 关联的会话 ID
    private MemoryType type;      // 记忆类型 (EVENT, USER_PROFILE, AGENT_PROFILE, RELATIONSHIP)
    private String content;       // 记忆的文本内容
    private float[] embedding;    // 文本内容的向量表示
    private Map<String, Object> metadata; // 元数据，用于高级过滤和加权
    private String source;        // 来源，如哪几轮对话的 ID
    // metadata 示例:
    // {
    //   "timestamp": "2026-02-14T10:00:00Z",
    //   "tags": ["work", "project-alpha"],
    //   "importance": 0.8, // 由 LLM 评估或用户反馈得出的重要性评分
    //   "access_count": 5,
    //   "last_accessed_at": "2026-02-14T12:30:00Z"
    // }
}

public enum MemoryType {
    EVENT, USER_PROFILE, AGENT_PROFILE, RELATIONSHIP
}
```

**索引策略**:

采用**混合索引**策略是关键。

1.  **向量索引**: 在 `VectorStore` 中，`embedding` 字段是主要的索引对象。这使得我们可以通过语义相似度来检索记忆，这是 RAG 的基础。
2.  **业务维度过滤**: 在检索时，仅仅依赖向量相似度是不够的。我们需要结合元数据（metadata）进行**前置过滤（Pre-filtering）** 或 **后置过滤（Post-filtering）**。例如，在向量搜索之前，先通过 `userId` 筛选出属于当前用户的记忆。Spring AI 的 `VectorStore` 接口通常支持通过 `SearchRequest` 传入元数据过滤器。[[718]](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)

```java
// 使用 Spring AI 的 SearchRequest 进行带元数据过滤的向量检索
SearchRequest request = SearchRequest.query("用户上次提到的项目进展")
    .withTopK(5)
    .withSimilarityThreshold(0.7)
    .withFilterExpression("userId == 'user-123' && importance > 0.5");

List<Document> results = vectorStore.similaritySearch(request);
```

这种“先过滤，再搜索”的模式，能极大地提升大规模数据下的检索性能和相关性。

### 3.3. 生成/检索逻辑

#### 中期记忆增量更新流程 (在线/准实时)

1.  **触发**: 在对话服务中设置一个计数器，当一个会话内未被总结的对话轮数达到阈值 N (e.g., 10) 时，或对话结束时触发。
2.  **异步任务**: 发布一个异步事件（如 Spring ApplicationEvent），由一个 `@Async` 方法消费。
3.  **上下文准备**: 任务处理器从 `SessionStore` 获取最近 N 轮对话历史，并从 `MemoryStore` 获取该会话最新的几条中期记忆。
4.  **Prompt 构建**: 构建一个用于“增量摘要”的 Prompt，将“已有记忆”和“新增对话”组合起来，要求 LLM 更新或新增事件、画像描述。
5.  **LLM 调用**: 使用 `ChatClient` 调用一个专门用于总结的模型。
6.  **Diff 与存储**:
    - 解析 LLM 返回的结构化数据（如 JSON）。
    - 与之前的记忆进行比对（diff），找出新增或变更的记忆条目。
    - 对新条目，使用 `EmbeddingClient` 生成向量。
    - 将新条目（文本、元数据、向量）写入 `MemoryStore` 和 `VectorStore`。

#### 长期记忆沉淀流程 (离线批处理)

1.  **触发**: 使用 `@Scheduled` 注解配置一个定时任务，在每日凌晨（业务低峰期）执行。
2.  **目标用户筛选**: 任务从数据库中筛选出当日活跃度较高的用户。
3.  **数据聚合**: 对于每个目标用户，聚合其当日产生的所有中期记忆和已有的长期记忆。
4.  **Prompt 构建**: 构建一个用于“高度抽象”的 Prompt，要求 LLM 对全量记忆进行总结、提炼和去重，更新长期画像。
5.  **LLM 调用与存储**: 调用一个能力更强、更擅长总结的 LLM。将返回的、更精炼的长期记忆更新回 `MemoryStore` 和 `VectorStore` 中，可以采用覆盖或版本化的方式。

#### 混合召回流程 (被动 + 主动 FC/RAG)

1.  **被动召回 (每次请求)**:
    - 在构建主聊天 Prompt 时，固定从 `MemoryStore` 中读取该用户的长期记忆（如 Persona）和几条最新的、最重要的中期记忆。
    - 将这些内容作为 `System` 或前置 `User` message 的一部分，拼接到上下文中。

2.  **主动召回 (工具调用)**:
    - **工具定义**: 在 Spring AI 中，通过 `@Bean` 定义一个 `java.util.function.Function` 作为工具，并使用 `@Description` 注解描述其功能。

    ```java
    @Bean
    @Description("根据自然语言查询，从用户过往的记忆中检索相关信息")
    public Function<ToolRequest, String> getMemory(RAGOrchestrator ragOrchestrator) {
        return request -> ragOrchestrator.retrieveMemory(request.query());
    }
    // ToolRequest 是一个简单的包装类，用于 Spring AI 正确解析参数
    public record ToolRequest(String query) {}
    ```

    - **工具注册**: 将这个 Function Bean 注册到 `ChatClient` 的 `Prompt` 构建流程中。

    ```java
    // 在对话服务中
    ChatResponse response = chatClient.prompt()
        .options(OpenAiChatOptions.builder().withTools(Set.of("getMemory")).build())
        .user(userMessage)
        .call()
        .chatResponse();
    ```

    - **触发与执行**:
      a.  用户提问：“我上次说我喜欢什么电影来着？”
      b.  `ChatClient` 将问题连同工具定义一起发给 LLM。
      c.  LLM 判断需要调用 `getMemory` 工具，返回一个 `FunctionCall`，其中 `query` 参数可能是“用户喜欢的电影”。
      d.  Spring AI 框架自动路由到 `getMemory` 这个 Bean，执行 `RAGOrchestrator` 的检索逻辑。
      e.  `RAGOrchestrator` 使用 `EmbeddingClient` 将 `query` 向量化，然后调用 `VectorStore.similaritySearch()`，并可能结合元数据进行 rerank（如下所述）。
      f.  检索到的记忆片段被格式化成字符串，作为 `tool` 角色的消息再次送入 `ChatClient`。
      g.  LLM 基于补充的记忆信息，生成最终回复：“你上次提到你很喜欢《银翼杀手》。”

    - **多维 Rerank**: 在 `RAGOrchestrator` 中，从 `VectorStore` 取回 Top-K 个候选记忆后，不应直接返回，而应进行 rerank，以提升最终结果的相关性。
        - **时间衰减**: 优先考虑最近发生的记忆。
        - **重要度**: `importance` 评分高的记忆优先。
        - **多样性**: 避免返回内容高度相似的多条记忆，可以使用 MMR (Maximal Marginal Relevance) 算法进行去重和多样化。

### 3.4. 治理策略

- **去重与压缩**: 在长期记忆沉淀过程中，明确要求 LLM 进行去重和合并相似记忆。
- **TTL/软删除**: 为记忆记录增加 `expires_at` 字段或 `is_deleted` 标记，实现“到期遗忘”或“逻辑遗忘”。用户请求删除记忆时，执行软删除，并可在后续的批处理中物理清除。
- **用户反馈/权重学习**: 设计 API，接收用户对 Agent 回忆的反馈（如“这条记得对”、“这条记错了”）。将这些反馈信号转化为对 `MemoryRecord` 中 `importance` 评分的调整，形成一个简单的学习闭环。
- **隐私合规与审计**: 对所有存储的敏感信息（如 PII）进行加密；建立严格的访问控制和审计日志，记录每一次对记忆的读写操作。

### 3.5. 选型建议

选择合适的存储后端是平衡成本、性能和运维复杂度的关键。

| 方案组合 | 优点 | 缺点 | 推荐场景 |
| :--- | :--- | :--- | :--- |
| **PostgreSQL + PGVector** | **高度集成**: 单一数据库，简化运维和数据一致性。**事务支持**: 可以在同一个事务中更新业务表和向量表。**生态成熟**: PostgreSQL 社区强大，工具链完善。Spring Data JPA 和 Spring AI 的 `PgVectorStore` 支持良好。[[718]](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html) | **性能瓶颈**: 在超大规模（百亿级向量）下，性能可能不及专用向量库。**扩展性**: 水平扩展相对复杂。 | **初创到中等规模项目**。当业务数据与向量数据需要强一致性，且运维团队希望简化技术栈时，是首选方案。 |
| **MySQL + Milvus/Qdrant** | **专业性能**: Milvus/Qdrant 是专为向量检索设计的，在性能、扩展性和高级功能（如标量过滤、分区）上更优。**架构解耦**: 业务数据和向量数据分离，可以独立扩展和优化。 | **运维复杂**: 需要维护两套独立的存储系统。**数据同步**: 需要额外的机制保证 RDBMS 和向量库之间的数据一致性。 | **大规模、高并发项目**。当向量检索的性能成为瓶颈，或预计数据量将达到数十亿级别时，应考虑此方案。 |
| **MySQL + Redis (with RediSearch)** | **低延迟**: Redis 作为内存数据库，能为向量检索提供极低的延迟。**多模型支持**: Redis 不仅是缓存，也是一个多模型数据库，可以同时处理键值、集合、向量等。 | **成本较高**: 基于内存，同等容量下成本高于磁盘存储。**持久化与高可用**: 需要更精细的配置来保证数据持久化和集群高可用。 | **对检索延迟极度敏感**的场景。或者项目中已经重度使用 Redis，希望复用现有基础设施。 |

**推荐**：对于大多数新项目，建议从 **PostgreSQL + PGVector** 开始，它的简便性和足够好的性能可以满足早期到中等规模的需求。随着业务发展，如果遇到性能瓶颈，再考虑向 Milvus 或 Qdrant 迁移。

### 3.6. Spring AI 生态映射

Spring AI 的设计哲学是“用 Spring 的方式做 AI”，它通过自动配置和统一的接口，大大降低了集成难度。

- **模型配置**: 在 `application.properties` 或 `application.yml` 中配置 `ChatModel` (如 OpenAI) 和 `EmbeddingModel` 的 API Key 和模型名称即可。Spring Boot 会自动创建相应的 `ChatClient` 和 `EmbeddingClient` Bean。[[716]](https://howtodoinjava.com/spring-ai/chatbot-example-with-rag/)

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
      embedding:
        options:
          model: text-embedding-3-small
```

- **VectorStore 接入**: 只需添加相应的 `starter` 依赖（如 `spring-ai-starter-vector-store-pgvector`），并配置好数据源信息，Spring Boot 就会自动为你配置好一个可注入的 `VectorStore` Bean。[[718]](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_memory
    username: user
    password: password
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
```

- **会话历史 (ChatMemory)**: Spring AI 提供了 `ChatMemory` 接口和多种实现，如 `InMemoryChatMemory`。对于生产环境，可以自定义实现，将其后端对接到 Redis 或数据库中，以 `(userId, sessionId)` 作为 Key 来存储对话历史。

- **工具调用 (Function Calling)**: 如 3.3.4 节所示，只需将一个带有 `@Description` 的 `java.util.function.Function` 类型的 Bean 暴露出来，Spring AI 就能将其自动注册为可供 LLM 调用的工具。

### 3.7. 逻辑级步骤示例

以下是一个简化的服务写入和召回记忆的时序描述：

**写入记忆 (中期，异步)**
1.  **[API Gateway/Controller]**: 接收到用户 `/chat` 请求。
2.  **[ChatService]**: 处理业务逻辑，调用 `ChatClient` 获取 LLM 回复。
3.  **[ChatService]**: 将当前对话（请求+回复）存入 `SessionStore` (e.g., Redis List)。
4.  **[ChatService]**: 检查会话的 `unsummarized_count`。若达到阈值：
    - 发布 `SummarizeMemoryEvent` 事件。
5.  **[MemoryEventHandler (`@Async`)]**: 监听到事件。
    - 从 `SessionStore` 和 `MemoryStore` 加载上下文。
    - 调用 `ChatClient` (使用总结模型) 生成摘要。
    - 调用 `EmbeddingClient` 为新记忆生成向量。
    - 调用 `MemoryStore` 和 `VectorStore` 的 `save` 方法持久化。
6.  **[ChatService]**: 将 LLM 回复返回给用户。

**召回记忆 (主动，同步)**
1.  **[API Gateway/Controller]**: 接收到用户 `/chat` 请求，内容为“我们上次聊到哪个项目了？”。
2.  **[ChatService]**: 调用 `chatClient.prompt().withTool("getMemory")...`。
3.  **[Spring AI Framework]**: 将请求和工具定义发送给 LLM。
4.  **[LLM]**: 返回 `FunctionCall`，调用 `getMemory`，`query` 为“上次聊到的项目”。
5.  **[Spring AI Framework]**: 查找名为 "getMemory" 的 `Function` Bean，并执行它。
6.  **[RAGOrchestrator]**:
    - 调用 `EmbeddingClient` 将 `query` 向量化。
    - 调用 `vectorStore.similaritySearch()` 检索出相关 `Document` 列表。
    - 对列表进行 Rerank (时间、重要度等)。
    - 将结果格式化为纯文本。
7.  **[Spring AI Framework]**: 将 RAG 结果作为 `tool` 消息，连同原始上下文再次调用 `ChatClient`。
8.  **[LLM]**: 基于补充信息，生成最终回答“我们上次聊到了‘天狼星’项目，似乎遇到了一些挑战。”
9.  **[ChatService]**: 将最终回答返回给用户。

## 4. 实施里程碑与验收

将记忆系统从概念落地到生产，建议采用分阶段实施和验证的方式。

### 第一阶段：PoC (概念验证)
- **目标**: 验证核心技术链路的可行性。
- **内容**:
    - 搭建基础架构：Java + Spring Boot + Spring AI。
    - 实现中期记忆的增量摘要流程（可手动触发）。
    - 实现基于 Function Calling 的主动召回（RAG）流程。
    - 使用 PostgreSQL + PGVector 作为存储。
    - 构建一个小型的、固定的测试用例集（如 5-10 个包含记忆点的对话脚本）。
- **验收标准**:
    - **召回准确率**: 对于测试用例中的问题，RAG 召回的记忆片段相关性 > 80%。
    - **一致性评分**: 在连续对话中，Agent 不应出现明显的人设或事实前后矛盾（人工评估）。
    - **时延**: 主动召回的全流程（用户提问 -> RAG -> 返回答案）平均时延 \u003c 3s。

### 第二阶段：灰度实验
- **目标**: 在小范围真实用户中验证系统效果与稳定性，并收集反馈。
- **内容**:
    - 完善长期记忆的离线沉淀流程。
    - 增加被动召回逻辑。
    - 建立基础的监控看板，监控 LLM 调用次数、Token 消耗、数据库负载、接口时延。
    - 开发用户反馈接口（如“赞/踩”某个回忆）。
    - 开放给 1% 的种子用户。
- **验收标准**:
    - **用户反馈**: 收集定性反馈，了解用户对“被记住”的感知和评价；正面反馈率 > 60%。
    - **成本控制**: 单个活跃用户每日的记忆相关平均成本在预算范围内。
    - **系统稳定性**: 灰度期间，记忆系统相关的服务错误率 \u003c 0.1%。

### 第三阶段：全量上线与持续迭代
- **目标**: 全量开放，并建立数据驱动的持续优化机制。
- **内容**:
    - 全量开放记忆系统。
    - 完善治理策略，特别是基于用户反馈的权重学习和“遗忘”机制。
    - 建立更全面的自动化评估体系，定期回归测试记忆质量。
    - 探索更高阶的 RAG 策略，如多路召回、图 RAG 等。
- **验收标准**:
    - **核心业务指标**: 记忆系统的上线对核心业务指标（如用户留存率、对话轮数、用户满意度）带来可衡量的正向提升。
    - **A/B 测试**: 对新的记忆策略（如不同的 Rerank 算法、Prompt 范式）进行 A/B 测试，验证其效果。
    - **风险可控**: 建立完善的隐私合规审计流程，确保用户数据安全，并能快速响应与记忆相关的客诉问题。

通过以上分阶段的实施路径，可以稳健地将复杂的记忆系统融入现有产品，在控制风险的同时，逐步释放其在提升用户体验和构建长期情感连接方面的巨大潜力。
