# Structured Mid-Memory + Rerank + Active Recall Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement a production-usable memory optimization path with structured mid-term memory, hybrid rerank, and an active recall tool.

**Architecture:** Keep current three-layer memory pipeline, add a structured mid-memory lane in parallel, then unify recall candidates from vector + structured stores and rerank before returning to model/tool.

**Tech Stack:** Java 21, Spring Boot, Spring AI Tool Calling, JdbcTemplate, PostgreSQL/pgvector (with in-memory fallback), JUnit 5.

### Task 1: Add RED tests for core behavior

**Files:**
- Create: `src/test/java/com/yupi/yuaiagent/chatmemory/StructuredMidMemoryServiceTest.java`
- Create: `src/test/java/com/yupi/yuaiagent/chatmemory/MemoryRerankServiceTest.java`
- Create: `src/test/java/com/yupi/yuaiagent/tools/MemoryRecallToolTest.java`

**Steps:**
1. Add failing test for extracting structured memories from evicted dialogue.
2. Add failing test for rerank ordering with recency/importance/keyword overlap.
3. Add failing test for recall tool response containing ranked, explainable items.

### Task 2: Implement structured mid-memory service

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/model/MemoryCandidate.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/model/StructuredMidMemoryRecord.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/StructuredMidMemoryService.java`

**Steps:**
1. Add extraction logic for preference/constraint/event/emotion/date snippets.
2. Add storage logic to `memory_record_v2` with `JdbcTemplate` and local fallback.
3. Add retrieval method returning candidate list for rerank.
4. Ensure all key paths have INFO/WARN/ERROR logs.

### Task 3: Implement hybrid rerank and active recall tool

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/MemoryRerankService.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/HybridMemoryRecallService.java`
- Create: `src/main/java/com/yupi/yuaiagent/tools/MemoryRecallTool.java`
- Modify: `src/main/java/com/yupi/yuaiagent/chatmemory/VectorMemoryService.java`

**Steps:**
1. Add vector candidate query API in `VectorMemoryService`.
2. Add score model combining similarity/recency/importance/keyword overlap.
3. Merge candidates from vector + structured sources and rerank.
4. Expose recall as `@Tool` API with stable output text.

### Task 4: Integrate with existing orchestration path

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/chatmemory/TieredChatMemoryAdvisor.java`
- Modify: `src/main/java/com/yupi/yuaiagent/tools/ToolRegistration.java`
- Modify: `src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- Modify: `src/main/java/com/yupi/yuaiagent/orchestration/OrchestrationService.java`
- Modify: `src/main/resources/application.yml`

**Steps:**
1. Async persist structured mid-memory from evicted messages.
2. Register tool and include it in safe chat whitelist/alias map.
3. Route “history recall” queries to TOOL mode and add prompt guidance.
4. Add memory rerank related config defaults.

### Task 5: Verify and report

**Files:**
- None (run commands only)

**Steps:**
1. Run targeted tests for newly added classes.
2. Run compile check.
3. Report what passed, what was skipped, and why.
