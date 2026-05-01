---
name: "pipeline-approve"
description: "通过 CR 审查并推进到下一阶段。仅在 CR₁/CR₂ 阶段有效。Invoke when user runs /pipeline:approve, or says 通过、approve、CR通过。"
---

# pipeline-approve

通过 CR 审查并自动推进到下一阶段。仅在阶段 3（CR₁）或阶段 6（CR₂）有效。

## 触发条件

用户输入 `/pipeline-approve [备注]` 时激活。

---

## 执行步骤

### 1. 读取 pipeline-state.json

文件不存在 → 报错停止。

### 2. 阶段校验

`currentStage` 不是 3 或 6 → 报错："approve 仅在 CR 阶段有效"。

### 3. 更新门禁

- 阶段 3 → `gates.cr1 = { status: "approved", at, note }`
- 阶段 6 → `gates.cr2 = { status: "approved", at, note }`

### 4. 推进阶段

- 3 → 4（Builder），6 → 7（Committer）
- history 追加 approve + enter 记录

### 5. 写入 JSON + 更新 markdown

### 6. 启动子 Agent

- → 4: Builder（`knowledge/current/roles/builder.md`）
- → 7: Committer（`knowledge/current/roles/committer.md`）

---

## JSON 操作规范

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）
- 全量写回，history 只追加，ISO 8601 时间戳
