# Case Design Checklist

Use this checklist when creating each capability test class.

## Coverage

- One test class for one capability.
- Include exactly three case types at minimum:
  - normal
  - boundary
  - exception

## Scenario Quality

- Use user-style prompts with concrete context (city, role, goal, conflict).
- Keep inputs concise enough for readable logs.
- Define expected behavior as key markers, not full text equality.

## Assertions

- Assert non-null response or expected exception.
- Assert key fields, keywords, or flags.
- Avoid strict full-copy matching for LLM text.

## Logging

- Log `start`, `invoke`, `assert`, `done` steps.
- Use `[TestFlow-<capability>]` prefix consistently.
- Include `caseId`, `step`, `input`, `expect`, `actual`, `durationMs`.
- Keep `input` and `actual` summarized.

## Execution Modes

- Default mode: offline stubs (`@TestConfiguration` + `@Primary`).
- Optional real-chain mode: gated by `RUN_AI_TESTS=true`.
