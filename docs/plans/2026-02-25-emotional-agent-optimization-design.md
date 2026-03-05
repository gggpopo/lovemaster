# 全能情感助手 — 多 Agent 协作架构优化方案

> 日期：2026-02-25
> 方案：全面重写核心层（方案B）
> 目标：解决耦合问题 + 打造情感类 Agent 差异化特色 + 定义 Claude Agent Team 协作角色

---

## 一、现状问题分析

### 1.1 耦合问题

| 问题 | 现状 | 影响 |
|------|------|------|
| LoveApp 大杂烩 | 一个类承担 5 种聊天模式（sync/stream/vision/tool/rag） | 添加新模式需改动核心类，测试困难 |
| 编排层臃肿 | OrchestrationService 600+ 行，意图路由/策略/执行全耦合 | 无法独立测试路由逻辑，修改一处影响全局 |
| 工具注册僵硬 | ToolRegistration 手动实例化，LoveApp 硬编码白名单 | 添加新工具需改 2-3 处代码 |
| Agent 单一 | 只有 YuManus 一个通用 Agent，无专业化分工 | 无法针对不同场景提供专业回应 |
| 情绪分析原始 | EmotionDetectTool 仅关键词匹配 6 种情绪，无强度/趋势 | 无法真正理解用户情感状态 |

### 1.2 功能缺失（对比优秀情感 Agent）

| 缺失能力 | 说明 |
|----------|------|
| 情绪趋势追踪 | 无法感知用户情绪随时间的变化 |
| 主动关怀 | 纯被动响应，不会主动发起关心 |
| 回复质量自检 | 无 Reflection 机制，回复质量不稳定 |
| 情感记忆 | 记忆系统不区分情感重要性，关键时刻容易被遗忘 |
| 关系健康度 | 前端的关系阶段基于消息数量，非真实关系质量 |
| 多 Agent 协作 | 无法让专业 Agent 处理专业场景 |

---

## 二、新架构设计

### 2.1 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     Controller Layer                         │
│   AiController / ConversationController / WebSocket          │
│              （保留现有 REST API + 新增 WS）                   │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                  AgentOrchestrator（新核心）                   │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │ Intent      │→ │ Agent        │→ │ Execution          │  │
│  │ Analyzer    │  │ Selector     │  │ Coordinator        │  │
│  │ (LLM+规则)  │  │ (意图→Agent) │  │ (并行/串行调度)     │  │
│  └─────────────┘  └──────────────┘  └────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │ EventBus（Spring ApplicationEvent）
┌────────────────────────▼────────────────────────────────────┐
│                Specialized Agent Layer                        │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Emotion      │  │ Companion    │  │ Date             │   │
│  │ Analyst      │  │ Agent        │  │ Planner          │   │
│  │ 情绪感知+趋势 │  │ 日常陪伴+共情 │  │ 约会策划+推荐    │   │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Conflict     │  │ Reflection   │  │ Memory           │   │
│  │ Mediator     │  │ Agent        │  │ Curator          │   │
│  │ 冲突调解+修复 │  │ 回复自检+优化 │  │ 记忆整理+巩固    │   │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                 Shared Infrastructure                         │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐ │
│  │ Tool       │ │ Cognitive  │ │ Context    │ │ Event    │ │
│  │ Registry   │ │ Memory     │ │ Engine     │ │ Bus      │ │
│  │ 注解扫描    │ │ 四层认知    │ │ GSSC管道   │ │ 事件驱动  │ │
│  └────────────┘ └────────────┘ └────────────┘ └──────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心设计原则

1. **单一职责**：每个 Agent 只做一件事，做到极致
2. **事件驱动**：Agent 之间通过 EventBus 通信，零直接依赖
3. **注解驱动**：工具和 Agent 通过注解自动注册，零硬编码
4. **感知-理解-回应-反思**：情感处理四阶段闭环
5. **记忆即人格**：通过认知记忆系统构建持续的情感人格

---

## 三、6 个专业 Agent 详细设计

### 3.1 EmotionAnalyst（情绪分析师）

**职责**：实时感知用户情绪状态，提供情绪画像供其他 Agent 使用

**触发条件**：每条用户消息到达时并行触发（不阻塞主流程）

**核心能力**：
- 情绪分类：扩展至 12 种（开心、感动、期待、平静、困惑、焦虑、委屈、愤怒、失落、悲伤、恐惧、绝望）
- 情绪强度：0-10 分制（0=无感，5=中等，10=极端）
- 混合情绪：支持同时识别 2-3 种情绪及各自权重
- 趋势分析：基于最近 N 条消息的情绪变化方向（上升/稳定/下降）
- 危机检测：识别自伤/极端情绪信号，触发安全干预

**输出数据结构**：
```java
public record EmotionProfile(
    List<EmotionItem> emotions,      // [{type: SAD, intensity: 7, weight: 0.6}]
    EmotionTrend trend,              // DECLINING / STABLE / IMPROVING
    double overallValence,           // -1.0 ~ 1.0 (负面到正面)
    boolean crisisDetected,          // 危机信号
    String narrativeSummary          // "用户表现出较强的失落感，伴随轻微焦虑"
) {}
```

**实现方式**：LLM 结构化输出（JSON mode），不再依赖关键词匹配

### 3.2 CompanionAgent（情感陪伴师）

**职责**：日常陪伴、共情回应、情绪安抚、主动关怀

**触发条件**：意图为 CHITCHAT / EMOTION_SUPPORT 时作为主 Agent

**核心能力**：
- 共情回应：根据 EmotionProfile 调整语气和回应策略
- 情绪安抚：针对负面情绪提供阶梯式安抚（倾听→共情→引导→赋能）
- 主动关怀：检测到情绪持续低落时，主动发起关心话题
- 人格一致性：维持稳定的陪伴者人格（温暖、耐心、不评判）
- 话题引导：在用户情绪好转时，自然引导到积极话题

**回应策略矩阵**：

| 情绪强度 | 正面情绪 | 中性情绪 | 负面情绪 |
|---------|---------|---------|---------|
| 低(1-3) | 轻松互动 | 日常陪聊 | 温和关心 |
| 中(4-6) | 分享喜悦 | 深入交流 | 共情倾听 |
| 高(7-10) | 一起庆祝 | 探索内心 | 紧急安抚 |

### 3.3 DatePlanner（约会策划师）

**职责**：约会策划、地点推荐、礼物建议、浪漫创意

**触发条件**：意图为 DATE_PLANNING / GIFT_ADVICE 时作为主 Agent

**核心能力**：
- 个性化推荐：基于用户偏好记忆（MemoryCurator 提供）推荐地点
- 场景化方案：根据关系阶段（初识/暧昧/热恋/稳定）定制方案
- 预算感知：根据用户经济状况调整推荐档次
- 天气联动：结合天气 API 推荐室内/室外活动
- 创意生成：生成独特的约会创意和浪漫惊喜方案

**可用工具**：DateLocationTool、WeatherTool、DateCalendarTool、WebSearchTool

### 3.4 ConflictMediator（冲突调解师）

**职责**：关系冲突分析、沟通策略指导、冷战修复、分手挽回

**触发条件**：意图为 CONFLICT_RESOLUTION / COLD_WAR / BREAKUP_RECOVERY 时

**核心能力**：
- 冲突诊断：分析冲突根源（沟通方式/价值观/期望差异/外部压力）
- 立场平衡：不偏袒任何一方，引导用户换位思考
- 分步调解：提供可执行的分步骤修复方案
- 话术指导：生成具体的沟通话术模板
- 风险评估：评估关系修复的可能性和最佳时机

**调解流程**：
```
倾听诉求 → 情绪确认 → 冲突诊断 → 换位引导 → 策略建议 → 话术生成 → 跟进计划
```

### 3.5 ReflectionAgent（回复质量审查官）

**职责**：对其他 Agent 的回复进行质量自检和优化

**触发条件**：每次主 Agent 生成回复后，在返回用户前触发

**评估维度**（每项 1-10 分）：
- 共情度：回复是否真正理解了用户的情感需求
- 语气适配：语气是否匹配当前情绪状态和场景
- 实用性：建议是否具体可执行
- 安全性：是否存在不当内容或可能造成伤害的建议
- 文化敏感性：是否考虑了文化背景和社会规范

**工作模式**：
- 快速模式（默认）：综合评分 > 7 分直接放行，< 7 分触发优化
- 严格模式（高风险场景）：冲突调解、分手挽回等场景强制优化
- 跳过模式：简单闲聊不触发反思

**输出**：优化后的回复 + 质量评分报告（存入日志，不展示给用户）

### 3.6 MemoryCurator（记忆管理员）

**职责**：对话记忆的整理、标签化、重要性评分、长期巩固

**触发条件**：
- 实时：每轮对话结束后异步触发
- 定时：每日凌晨执行记忆巩固任务

**核心能力**：
- 情感标签：为每段记忆打上情感标签（情绪类型+强度）
- 重要性评分：0.0-1.0（关键偏好 0.8-1.0，日常互动 0.5-0.7，临时上下文 0.3-0.5）
- 记忆巩固：将重要性 > 0.7 的工作记忆提升为长期记忆
- 记忆衰减：对低重要性记忆执行时间衰减 `exp(-0.1 * age_days)`
- 用户画像更新：从对话中提取偏好、习惯、关系状态等更新用户画像

**记忆分层**：

| 层级 | 存储 | 容量 | TTL | 用途 |
|------|------|------|-----|------|
| 工作记忆 | 内存 | 50条 | 60min | 当前对话上下文 |
| 情景记忆 | Redis/File | 无限 | 持久 | 历史对话事件（带时间戳+情感标签） |
| 语义记忆 | 向量库 | 无限 | 持久 | 用户偏好、关系知识、抽象概念 |
| 用户画像 | JSON | 1份/用户 | 持久 | 结构化用户信息（年龄/性格/偏好/关系状态） |

---

## 四、Agent 协作流程

### 4.1 主流程（每条用户消息）

```
用户消息到达
    │
    ├──→ EmotionAnalyst（并行，不阻塞）──→ EmotionProfile
    │
    ├──→ IntentAnalyzer ──→ 意图分类
    │         │
    │         ▼
    │    AgentSelector（根据意图 + EmotionProfile 选择主 Agent）
    │         │
    │         ▼
    │    ContextEngine.gather()（GSSC 管道组装上下文）
    │         │
    │         ▼
    │    主 Agent 执行（Companion / DatePlanner / ConflictMediator）
    │         │
    │         ▼
    │    ReflectionAgent 质量自检
    │         │
    │         ▼
    │    返回用户
    │
    └──→ MemoryCurator（异步，对话后整理记忆）
```

### 4.2 Agent 选择策略

```java
// AgentSelector 核心逻辑
public SpecializedAgent select(Intent intent, EmotionProfile emotion) {
    // 危机优先：检测到危机信号，强制切换到安全干预
    if (emotion.crisisDetected()) return safetyInterventionAgent;

    // 情绪优先：高强度负面情绪时，优先情感陪伴
    if (emotion.overallValence() < -0.5 && emotion.maxIntensity() > 7) {
        return companionAgent;
    }

    // 意图驱动：正常情况按意图路由
    return switch (intent.type()) {
        case CHITCHAT, EMOTION_SUPPORT -> companionAgent;
        case DATE_PLANNING, GIFT_ADVICE -> datePlannerAgent;
        case CONFLICT_RESOLUTION, COLD_WAR, BREAKUP_RECOVERY -> conflictMediatorAgent;
        case LOVE_COPYWRITING -> companionAgent; // 带文案生成工具
        case IMAGE_REQUEST -> companionAgent;     // 带视觉工具
        default -> companionAgent;
    };
}
```

### 4.3 事件驱动通信

Agent 之间不直接调用，通过 Spring ApplicationEvent 通信：

```java
// 事件定义
public record EmotionAnalyzedEvent(String chatId, EmotionProfile profile) {}
public record AgentResponseEvent(String chatId, String agentName, String response) {}
public record MemoryConsolidationEvent(String chatId, List<Message> messages) {}
public record CrisisDetectedEvent(String chatId, EmotionProfile profile) {}

// Agent 监听事件
@EventListener
public void onEmotionAnalyzed(EmotionAnalyzedEvent event) {
    // CompanionAgent 根据情绪调整回应策略
}
```

---

## 五、基础设施重写

### 5.1 ToolRegistry（注解驱动工具注册）

**现状问题**：`ToolRegistration.allTools()` 手动实例化，`LoveApp.SAFE_CHAT_TOOL_NAMES` 硬编码白名单

**新设计**：

```java
// 工具注解
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface EmotionalTool {
    String name();
    String description();
    ToolDomain[] domains();        // EMOTION, DATE, CONFLICT, GENERAL
    ToolSafetyLevel safety();      // SAFE, REQUIRES_REVIEW, DANGEROUS
    String[] requiredAgents() default {};  // 限定哪些 Agent 可用
}

// 自动扫描注册
@Component
public class AnnotationToolRegistry implements ToolRegistry {
    @Resource
    private ApplicationContext context;

    @PostConstruct
    public void scanAndRegister() {
        context.getBeansWithAnnotation(EmotionalTool.class)
               .forEach(this::register);
    }

    public List<ToolCallback> getToolsForAgent(String agentName) {
        // 根据 Agent 名称和域过滤可用工具
    }
}
```

### 5.2 CognitiveMemory（认知记忆系统）

**现状问题**：三层存储但无情感标签、无重要性评分、无记忆巩固

**新设计**：在现有 TieredChatMemoryRepository 基础上增加认知层

```java
public interface CognitiveMemoryService {
    // 存储带情感标签的记忆
    void store(String chatId, Message message, EmotionProfile emotion);

    // 按情感相关性检索
    List<MemoryItem> recallByEmotion(String chatId, EmotionType type, int limit);

    // 按重要性检索
    List<MemoryItem> recallByImportance(String chatId, double minImportance);

    // 获取用户画像
    UserProfile getUserProfile(String userId);

    // 记忆巩固（定时任务）
    void consolidate(String chatId);

    // 记忆衰减（定时任务）
    void decay(String chatId);
}
```

### 5.3 ContextEngine（GSSC 上下文管道）

**参考 hello-agents 第九章的 GSSC 框架**：

```java
public class ContextEngine {
    /**
     * Gather → Select → Structure → Compress
     */
    public ContextPacket build(String chatId, String userMessage, EmotionProfile emotion) {
        // 1. Gather: 收集候选信息
        List<ContextItem> candidates = new ArrayList<>();
        candidates.addAll(workingMemory.getRecent(chatId, 10));
        candidates.addAll(episodicMemory.recall(chatId, userMessage, 5));
        candidates.addAll(semanticMemory.search(userMessage, 5));
        candidates.add(userProfile.get(chatId));

        // 2. Select: 评分过滤（relevance * 0.7 + recency * 0.3）
        List<ContextItem> selected = candidates.stream()
            .map(item -> item.withScore(
                item.relevance() * 0.7 + item.recency() * 0.3))
            .filter(item -> item.score() > 0.3)
            .sorted(Comparator.comparing(ContextItem::score).reversed())
            .toList();

        // 3. Structure: 组织为结构化上下文
        // [角色设定] [用户画像] [情绪状态] [相关记忆] [当前对话]

        // 4. Compress: 超出 token 预算时压缩
        return compress(structured, tokenBudget);
    }
}
```

### 5.4 EventBus（事件总线）

基于 Spring ApplicationEvent，轻量级实现：

```java
@Component
public class AgentEventBus {
    @Resource
    private ApplicationEventPublisher publisher;

    public void publish(AgentEvent event) {
        log.info("[EventBus] Publishing: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }

    // 支持异步事件
    @Async
    public void publishAsync(AgentEvent event) {
        publisher.publishEvent(event);
    }
}
```

---

## 六、情感特色功能

### 6.1 情绪仪表盘（前端新增）

```
┌─────────────────────────────────────┐
│         情绪趋势图（折线图）          │
│  😊 ─────╲                          │
│           ╲──── 😐                  │
│                  ╲──── 😢           │
│  最近7天情绪变化                      │
├─────────────────────────────────────┤
│  当前情绪：失落(7/10) + 焦虑(4/10)   │
│  情绪趋势：↓ 下降中                  │
│  关系健康度：72/100                   │
├─────────────────────────────────────┤
│  💡 建议：最近情绪波动较大，           │
│     要不要聊聊最近发生了什么？          │
└─────────────────────────────────────┘
```

### 6.2 关系健康度评估

基于多维度计算，替代现有的消息数量计算：

```
关系健康度 = 沟通质量(30%) + 情绪稳定性(25%) + 互动频率(20%) + 冲突解决率(15%) + 亲密度(10%)
```

### 6.3 主动关怀机制

```java
// 定时检查用户情绪状态
@Scheduled(fixedRate = 3600000) // 每小时
public void proactiveCheck() {
    // 1. 检查最近对话的情绪趋势
    // 2. 如果连续 3 次对话情绪下降，生成关怀消息
    // 3. 通过 WebSocket 推送给前端（非侵入式提示）
}
```

### 6.4 情感日记（自动生成）

每日自动生成情感日记摘要，帮助用户回顾情感变化：

```
📖 2026-02-25 情感日记
今天你和我聊了3次。上午你分享了约会的开心，
下午因为对方没回消息有些焦虑。
整体来看，今天的情绪是积极的，偶有小波动。
建议：给对方一些空间，忙完了自然会回复你的 ❤️
```

---

## 七、Claude Code Agent Team 角色定义

### 7.1 开发阶段 Agent 分工

用于 Claude Code 的 Task tool 并行开发，5 个子 Agent 各自独立工作：

#### Agent 1: Architect（架构师）

**负责模块**：
- `AgentOrchestrator`（新核心编排器）
- `IntentAnalyzer`（意图分析器）
- `AgentSelector`（Agent 选择器）
- `ExecutionCoordinator`（执行协调器）
- `AgentEventBus`（事件总线）

**工作范围**：
- 定义 `SpecializedAgent` 接口和生命周期
- 实现 AgentOrchestrator 替代 OrchestrationService
- 实现事件驱动通信机制
- 定义 Agent 注册和发现机制

**包路径**：`com.yupi.yuaiagent.orchestration.v2`

**交付物**：
- `SpecializedAgent.java`（接口）
- `AgentOrchestrator.java`
- `IntentAnalyzer.java`
- `AgentSelector.java`
- `ExecutionCoordinator.java`
- `AgentEventBus.java`
- 对应单元测试

---

#### Agent 2: Emotion Dev（情感系统开发者）

**负责模块**：
- `EmotionAnalystAgent`（情绪分析 Agent）
- `ReflectionAgent`（回复质量审查 Agent）
- `EmotionProfile` 数据模型
- 情绪趋势分析服务

**工作范围**：
- 实现 LLM 驱动的情绪分析（替代关键词匹配）
- 实现回复质量自检和优化流程
- 设计情绪数据模型和趋势计算
- 实现危机检测机制

**包路径**：`com.yupi.yuaiagent.agent.specialized.emotion`

**交付物**：
- `EmotionAnalystAgent.java`
- `ReflectionAgent.java`
- `EmotionProfile.java` / `EmotionItem.java` / `EmotionTrend.java`
- `EmotionTrendService.java`
- `CrisisDetector.java`
- 对应单元测试

---

#### Agent 3: Domain Dev（领域 Agent 开发者）

**负责模块**：
- `CompanionAgent`（情感陪伴 Agent）
- `DatePlannerAgent`（约会策划 Agent）
- `ConflictMediatorAgent`（冲突调解 Agent）

**工作范围**：
- 实现三个领域专业 Agent 的核心逻辑
- 为每个 Agent 设计专属 System Prompt
- 实现回应策略矩阵（根据情绪调整回应方式）
- 集成现有工具（DateLocationTool、WeatherTool 等）

**包路径**：`com.yupi.yuaiagent.agent.specialized.domain`

**交付物**：
- `CompanionAgent.java`
- `DatePlannerAgent.java`
- `ConflictMediatorAgent.java`
- `prompts/companion_system.txt`
- `prompts/date_planner_system.txt`
- `prompts/conflict_mediator_system.txt`
- 对应单元测试

---

#### Agent 4: Infra Dev（基础设施开发者）

**负责模块**：
- `AnnotationToolRegistry`（注解驱动工具注册）
- `CognitiveMemoryService`（认知记忆系统）
- `ContextEngine`（GSSC 上下文管道）
- `MemoryCuratorAgent`（记忆管理 Agent）

**工作范围**：
- 重写工具注册机制（注解扫描替代手动注册）
- 在现有 TieredChatMemoryRepository 上增加认知层
- 实现 GSSC 上下文组装管道
- 实现记忆巩固和衰减机制

**包路径**：
- `com.yupi.yuaiagent.tool.registry`
- `com.yupi.yuaiagent.memory.cognitive`
- `com.yupi.yuaiagent.context`

**交付物**：
- `@EmotionalTool` 注解 + `AnnotationToolRegistry.java`
- `CognitiveMemoryService.java` + `MemoryItem.java`
- `ContextEngine.java` + `ContextPacket.java`
- `MemoryCuratorAgent.java`
- `UserProfile.java` + `UserProfileService.java`
- 对应单元测试

---

#### Agent 5: Frontend Dev（前端开发者）

**负责模块**：
- `LoveMaster.vue` 升级
- 情绪仪表盘组件
- Agent 协作可视化
- 关系健康度面板

**工作范围**：
- 新增情绪趋势折线图（基于 ECharts/Chart.js）
- 新增关系健康度雷达图
- 升级 Agent 执行追踪面板（展示多 Agent 协作过程）
- 新增情感日记面板
- 适配新的 SSE 事件协议（增加 emotion_analyzed、reflection_completed 等事件）

**文件路径**：`yu-ai-agent-frontend/src/`

**交付物**：
- `components/EmotionDashboard.vue`
- `components/RelationshipHealth.vue`
- `components/AgentCollaboration.vue`
- `components/EmotionDiary.vue`
- 更新 `views/LoveMaster.vue`
- 更新 `api/index.js`

---

### 7.2 Agent 间依赖关系

```
Agent 1 (Architect) ──→ 定义接口和框架骨架
    │
    ├──→ Agent 2 (Emotion Dev)    ← 依赖 SpecializedAgent 接口
    ├──→ Agent 3 (Domain Dev)     ← 依赖 SpecializedAgent 接口
    └──→ Agent 4 (Infra Dev)      ← 依赖 EventBus、ToolRegistry 接口

Agent 5 (Frontend Dev) ──→ 可与后端并行开发（Mock API）
```

**建议执行顺序**：
1. Phase 1：Agent 1 先行，产出接口定义和框架骨架（约 1-2 小时）
2. Phase 2：Agent 2/3/4 并行开发（约 3-4 小时）
3. Phase 3：Agent 5 前端开发（可与 Phase 2 并行）
4. Phase 4：集成测试和联调

### 7.3 Claude Code 调用示例

```
# Phase 1: 架构师先行
Task(subagent_type="general-purpose", prompt="作为 Architect Agent，实现...")

# Phase 2: 三个 Agent 并行
Task(subagent_type="general-purpose", prompt="作为 Emotion Dev Agent，实现...")
Task(subagent_type="general-purpose", prompt="作为 Domain Dev Agent，实现...")
Task(subagent_type="general-purpose", prompt="作为 Infra Dev Agent，实现...")

# Phase 3: 前端并行
Task(subagent_type="general-purpose", prompt="作为 Frontend Dev Agent，实现...")
```

---

## 八、新增包结构

```
src/main/java/com/yupi/yuaiagent/
├── agent/
│   ├── base/                          # 保留现有 BaseAgent/ReActAgent/ToolCallAgent
│   ├── specialized/
│   │   ├── SpecializedAgent.java      # 新接口
│   │   ├── emotion/
│   │   │   ├── EmotionAnalystAgent.java
│   │   │   ├── ReflectionAgent.java
│   │   │   ├── CrisisDetector.java
│   │   │   └── model/
│   │   │       ├── EmotionProfile.java
│   │   │       ├── EmotionItem.java
│   │   │       └── EmotionTrend.java
│   │   └── domain/
│   │       ├── CompanionAgent.java
│   │       ├── DatePlannerAgent.java
│   │       └── ConflictMediatorAgent.java
├── orchestration/
│   └── v2/
│       ├── AgentOrchestrator.java     # 新核心
│       ├── IntentAnalyzer.java
│       ├── AgentSelector.java
│       ├── ExecutionCoordinator.java
│       └── event/
│           ├── AgentEventBus.java
│           ├── EmotionAnalyzedEvent.java
│           ├── AgentResponseEvent.java
│           └── CrisisDetectedEvent.java
├── memory/
│   └── cognitive/
│       ├── CognitiveMemoryService.java
│       ├── MemoryItem.java
│       ├── MemoryCuratorAgent.java
│       └── UserProfile.java
├── context/
│   ├── ContextEngine.java
│   └── ContextPacket.java
├── tool/
│   └── registry/
│       ├── EmotionalTool.java         # 注解
│       ├── AnnotationToolRegistry.java
│       └── ToolDomain.java
└── resources/
    └── prompts/
        ├── companion_system.txt
        ├── date_planner_system.txt
        ├── conflict_mediator_system.txt
        ├── emotion_analyst_system.txt
        └── reflection_system.txt
```

---

## 九、迁移策略

### 9.1 保留不动的部分
- Controller 层（AiController、ConversationController）
- 前端基础框架（Vue 3 + Vite）
- 现有工具实现（DateLocationTool、WeatherTool 等）
- 配置体系（application.yml、profiles）
- 日志和监控（TraceId、Logback）

### 9.2 重写的部分
- OrchestrationService → AgentOrchestrator
- LoveApp 的多模式逻辑 → 分散到各 SpecializedAgent
- ToolRegistration → AnnotationToolRegistry
- EmotionDetectTool → EmotionAnalystAgent（LLM 驱动）
- IntentRouter → IntentAnalyzer（保留规则+LLM 双轨）

### 9.3 新增的部分
- 6 个专业 Agent
- EventBus 事件通信
- CognitiveMemoryService 认知记忆
- ContextEngine GSSC 管道
- ReflectionAgent 回复自检
- 前端情绪仪表盘、关系健康度面板

### 9.4 兼容性保证
- 新旧编排器通过配置开关切换：`app.orchestration.version: v1 | v2`
- v1 保持现有行为不变
- v2 启用新的多 Agent 协作架构
- 前端通过 SSE 事件类型自动适配

---

## 十、成功指标

| 指标 | 当前 | 目标 |
|------|------|------|
| 情绪识别准确率 | ~60%（关键词匹配） | >85%（LLM 驱动） |
| 情绪类型数 | 6 种 | 12 种 + 强度 + 混合 |
| 回复共情度 | 无评估 | >7/10（ReflectionAgent 评分） |
| 工具添加成本 | 改 2-3 个文件 | 只需 1 个注解类 |
| Agent 扩展成本 | 改核心代码 | 实现接口 + 注册 |
| 记忆持续性 | 文件存储，无标签 | 四层认知记忆 + 情感标签 |
| 关系健康度 | 基于消息数量 | 多维度综合评估 |
