# Orchestrator — 流程调度与用户交互

> 前置：AGENTS.md 定义了全局规则（规则分级、源码修改守卫、架构约束等），本文件仅定义 Orchestrator 特有的调度逻辑。

---

# 子文件按需加载

| 文件 | 加载时机 | 内容 |
|------|---------|------|
| `orchestrator/tier.md` | `/pipeline:new`、阶段跳转时 | Tier 定义 + 跳过表 + 判定协议 |
| `orchestrator/reject.md` | CR2 打回时 | 打回分级 + 回退协议 |
| `orchestrator/commands.md` | `/pipeline:*` 命令时 | 命令表 + 参数 + 门禁校验 |

---

# 工作区留痕协议

`.knowledge-work/` 下三个文件构成跨会话记忆：

| 文件 | 职责 | 优先级 |
|------|------|--------|
| `pipeline-state.json` | 阶段编号、门禁状态、历史记录 | **事实源**（冲突时以此为准） |
| `progress.md` | 决策上下文、关键推导过程 | 视图 |
| `current-plan.md` | 当前计划和下一步 | 视图 |

**触发时机**（满足任一即触发，不可延迟）：子 Agent 返回后 / 用户 CR 结论后 / 会话即将结束时。

---

# 阶段流水线

| # | 阶段 | 角色/动作 | 产出 |
|---|------|----------|------|
| 1 | 探索需求 | Explorer | `exploration.md` |
| 2 | 设计方案 | Planner | `design.md` + `spec.md` + `tasks.md` + `unit-task.md` |
| 3 | 人工 CR1 | 用户审查 | approved / 打回 |
| 4 | 实现代码 | Builder | 业务代码 + 测试代码 |
| 5 | 构建验证 | Verifier | 构建 + 单测 + 覆盖度 |
| 6 | 人工 CR2 | 用户审查 | approved / 打回 |
| 7 | 代码提交 | Committer | git commit |
| 8 | 归档 | Archiver | 归档包 → `knowledge/delta/` |

变更按 Tier 分级走不同阶段子集（详见 `orchestrator/tier.md`）。

---

# 知识库新鲜度门禁

在 `/pipeline:new`、进入阶段 1/2 时检查 `knowledge/delta/` 数量 N：
- N < 3 → 静默通过
- 3 ≤ N < 5 → 提醒，不阻断
- N ≥ 5 → 强提醒，提供选项（现在合并 / 跳过继续）

---

# 子 Agent 调度

## 调度规则
```text
[MUST-DO] 按 roles 文件末尾「完成标准」验收，任一 FAIL 打回重做；
[MUST-DO] 单次 Builder 子 Agent 处理 task ≤ 3 个，超过分批；
```

## prompt 强制模板
```text
[ASSERT-DISALLOW] 发起子 Agent 时，prompt 必须遵循以下模板，禁止手写步骤替代 roles 文件。

模板：
─────────────────────────────────
你是 {角色名} 角色。

请先读取以下文件，按其中定义的完整流程执行：
- {roles文件绝对路径}
- {capabilities.yaml绝对路径}（若角色需要）

## 上下文
- 变更名称：{change}
- 工作目录：/Users/mi/project/QuickGamePlatform
- 设计文件位置：.knowledge-work/{change}/
- {amendment信息/失败报告/CR反馈等}

## 执行要求
按 {roles文件名} 中定义的完整流程执行，返回执行结果汇总。
{仅允许补充：amendment上下文、打回时的失败信息}
─────────────────────────────────

违规判定：
  - prompt 出现具体命令（./gradlew xxx）→ 违规
  - prompt 出现步骤列表（"1. 编译 2. 测试"）→ 违规
  - prompt 出现裁剪指令（"只做 X"/"跳过 Y"）→ 违规
  - 唯一例外：Builder 可附带具体修改清单
```

---

# 阶段切换校验
```text
[ASSERT-DISALLOW] 切换阶段前必须：
  1. 读取 pipeline-state.json 获取 currentStage
  2. 判断下一阶段类型：
     a) 人工门禁（阶段 3/6）→ 向用户请求审查结论
     b) 非人工门禁 → 立即发起子 Agent，禁止向用户确认

[ASSERT-DISALLOW] 非门禁阶段切换禁止任何形式的用户确认请求。
```

## 阶段切换检查清单

任一未完成禁止进入下一阶段：
- [ ] 子 Agent 已返回成功摘要
- [ ] `progress.md` + `current-plan.md` 已更新
- [ ] 产出文件已写入 `.knowledge-work/`
- [ ] roles 文件「完成标准」全部 PASS

---

# 硬性约束（Orchestrator 特有）

| # | 约束 | 说明 |
|---|------|------|
| 1 | 门禁不可跳过 | CR2 为所有 Tier 的底线门禁；跳过前须 `pipeline-guard stage-skip` PASS |
| 2 | unit-task.md 保护 | Builder/Verifier 只读，修改须用户确认后由 Planner 执行 |
| 3 | 验证失败打回 | 阶段 5 → 4 循环；第 3 次失败暂停流水线通知用户 |

---

# 代码路由

路由索引（v2）位于 `knowledge/current/router/`。
- Explorer/Planner 必须使用；Builder 推荐使用
- 查询：`Grep "^ClassName\t" symbol-table.tsv` / `method-index.tsv` / `call-graph.tsv`
- 完整协议：`router/QUERY-PROTOCOL.md`
