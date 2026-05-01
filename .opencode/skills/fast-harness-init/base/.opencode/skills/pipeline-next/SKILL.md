---
name: "pipeline-next"
description: "推进流水线到下一阶段，自动执行门禁校验。Invoke when user runs /pipeline:next, or asks to 下一步、推进、继续流水线。"
---

# pipeline-next

推进流水线到下一阶段，自动执行门禁校验。

## 触发条件

用户输入 `/pipeline-next [--force]` 时激活。

---

## 执行步骤

### 1. 读取 pipeline-state.json

文件不存在 → 报错停止。

### 2. 计算目标阶段

`targetStage = currentStage + 1`

- `currentStage = 8` → "已在最终阶段"，停止
- `currentStage = 3 或 6`（CR 阶段）且无 `--force` → 报错，提示用 approve/reject

**阶段编号映射**：`1=探索 2=设计 3=CR₁ 4=实现 5=构建验证 6=CR₂ 7=提交 8=归档`

### 3. 门禁校验

无 `--force` 时，按 targetStage 校验：

| 目标 | 校验条件 |
|------|---------|
| → 2 | `exploration.md` 存在 |
| → 3 | `design.md` + `spec.md` + `tasks.md` + `unit-task.md` 存在 |
| → 4 | `gates.cr1.status === "approved"` |
| → 5 | `git diff --stat` 有变更 |
| → 6 | `gates.build.status === "passed"` |
| → 7 | `gates.cr2.status === "approved"` |
| → 8 | history 中有 stage 7 完成记录 |

失败 → 报告原因 + 提示 `--force`。

`--force` → 跳过，history 记录 `forced: true` + `skippedGate`。

### 4. 阶段 5 特殊处理

Verifier 完成后需更新 `gates.build`，在 prompt 中明确要求。

**Verifier 子 Agent prompt 必须包含以下执行指令**：

```
你是 Verifier 角色。请先读取 knowledge/current/roles/verifier.md 了解完整职责。

执行分两阶段：

阶段 A（脚本步骤）：
  执行 capability:verify-all（查阅 capabilities.yaml 获取脚本路径），
  该脚本串联运行步骤 1(编译)/3(Lint)/4(Sonar)/5(复杂度)/6(密钥)。
  如果脚本输出 VERIFY_ALL_RESULT=FAIL，记录失败步骤并停止。

阶段 B（AI 分析步骤，仅在阶段 A 通过后执行）：
  步骤 2: 执行 ./gradlew test，分析结果（按 verifier.md 要求提取信息）
  步骤 2b: 读取 unit-task.md，比对实际测试方法覆盖度
  步骤 7: 读取 spec.md/tasks.md/design.md，执行方案实现对比验证

八步全部完成后汇总结果，更新 progress.md。
```

### 5. 更新 JSON + markdown

### 6. CR 阶段提示

→ 3 或 6 → 不启动子 Agent，提示等待 `/pipeline-approve` 或 `/pipeline-reject`。

### 7. 启动子 Agent

| 阶段 | 角色 | roles 文件 |
|------|------|-----------|
| 1 | Explorer | `knowledge/current/roles/explorer.md` |
| 2 | Planner | `knowledge/current/roles/planner.md` |
| 4 | Builder | `knowledge/current/roles/builder.md` |
| 5 | Verifier | `knowledge/current/roles/verifier.md` |
| 7 | Committer | `knowledge/current/roles/committer.md` |
| 8 | Archiver | `knowledge/current/roles/archiver.md` |

**Amendment 上下文注入**：当变更数据中存在 `amendment` 字段时，启动子 Agent 的 prompt 中追加：
```
注意：本变更为归档后修正（amendment）。
  修正原因：<amendment.reason>
  原 commit：<amendment.originalCommit>
  Scope：<amendment.scope>
请在原有实现基础上修正，而非从头实现。优先关注 CR 反馈指出的问题。
```

---

## JSON 操作规范

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）
- 全量写回，history 只追加，ISO 8601 时间戳
