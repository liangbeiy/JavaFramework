---
name: "pipeline-reject"
description: "CR 打回，回退到前序阶段并启动修复。仅在 CR₁/CR₂ 阶段有效。Invoke when user runs /pipeline:reject, or says 打回、reject、CR不通过。"
---

# pipeline-reject

CR 打回，回退到前序阶段并启动修复。仅在阶段 3（CR₁）或阶段 6（CR₂）有效。

## 触发条件

用户输入 `/pipeline-reject [原因]` 时激活。

---

## 执行步骤

### 1. 读取 pipeline-state.json

文件不存在 → 报错停止。

### 2. 阶段校验

`currentStage` 不是 3 或 6 → 报错："reject 仅在 CR 阶段有效"。

### 3. 回退阶段

- 阶段 3 → `currentStage = 2`（Planner）
- 阶段 6 → `currentStage = 4`（Builder）
- history 追加 reject（含原因）+ enter 记录

### 4. 写入 JSON + 更新 markdown

### 5. 启动子 Agent

- → 2: Planner（`knowledge/current/roles/planner.md`），prompt 含打回原因
- → 4: Builder（`knowledge/current/roles/builder.md`），prompt 含打回原因

---

## JSON 操作规范

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）
- 全量写回，history 只追加，ISO 8601 时间戳
