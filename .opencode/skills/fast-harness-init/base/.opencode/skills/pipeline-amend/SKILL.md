---
name: "pipeline-amend"
description: "归档后修正流程 — 团队 CR 打回后恢复工作区，按修正规模重新进入流水线。Invoke when user runs /pipeline:amend, or asks to 归档修正、amend。"
---

# pipeline-amend

归档后修正流程 — 团队 CR 打回后，从归档包恢复工作区，按修正规模重新进入流水线。

## 触发条件

用户输入 `/pipeline:amend <change> [--scope small|medium|large]` 时激活。

---

## 适用场景

代码已归档（阶段 8 完成）后，push 到 Gerrit 等平台的团队 CR 提出修改意见。此时流水线已结束、工作区已清理，需要一个标准化的"回退 → 修正 → 重新归档"路径。

---

## 前置条件

```
1. 变更已完成归档：
   - pipeline-state.json 的 completedChanges 中有该变更记录
   - 或 knowledge/delta/ 下存在 <change>-* 归档包目录
2. 归档包中设计文件完整（design.md / spec.md / tasks.md / unit-task.md）
3. 用户提供修正原因（团队 CR 反馈内容）
```

如果变更未找到归档记录（completedChanges 中无记录 + knowledge/delta/ 下无对应目录），报错停止：
```
❌ 变更 <change> 未找到归档记录。
  - 请确认变更名称正确
  - 如果 delta 已被 /archive-knowledge 合并删除，使用 --scope large 从头设计
```

---

## Scope 定义

| Scope | 适用条件 | 回退到的阶段 | 走哪些流程 |
|-------|---------|------------|-----------|
| `small` | 改动不影响知识库描述（命名调整、加注释、补 null check） | 不回退流水线 | 直接改代码 → Archiver 重新审计 |
| `medium` | 需改代码且影响知识库描述，但方案不变（改类名、方法签名、逻辑重构） | 阶段 4（Builder） | Builder → Verifier → Commit → Archive |
| `large` | CR 涉及方案级调整（架构改、接口重设计） | 阶段 2（Planner） | Planner → [CR₁] → Builder → Verifier → Commit → Archive |

## Scope 自动推断

当用户未显式指定 `--scope` 时，Orchestrator 根据 CR 反馈内容自动推断：

```
推断规则：
  读取用户描述的 CR 反馈内容，判断涉及的修改层级：

  → 只涉及命名/注释/格式/简单 null check/日志文案/未使用 import 清理
    → scope = small

  → 涉及实现逻辑但方案不变：改类名、改方法签名、重构方法内部实现、
    补充遗漏的边界处理、修复审查发现的 bug
    → scope = medium

  → 涉及方案级问题：架构选型要改、接口设计要重做、新增整个功能模块、
    需求理解有偏差导致方向错误
    → scope = large

推断后必须一次性向用户确认变更名和 scope（两项都确认）：
  输出：
    我判断：
    - 变更：<推断的变更名>
    - Scope：<推断结果>（原因：<一句话判断依据>）
    确认执行？

  用户确认 → 继续执行
  用户纠正任一项 → 按用户指定值执行
  用户全部否定 → 停止，请求用户提供正确信息
```

**变更名推断规则**：
- 用户显式指定了 `<change>` → 直接使用
- 用户未指定但提到了关键词（如"黑屏"对应 black-screen-report） → 从 completedChanges 模糊匹配
- completedChanges 只有一个变更 → 自动选定
- 无法确定 → 列出 completedChanges 请用户选择

---

## 执行协议

**6 个步骤按序执行。**

### 步骤 1：定位归档包

```
1. 读取 .knowledge-work/pipeline-state.json
2. 在 completedChanges 中查找 <change>：
   - 找到 → 记录原 commit hash、完成日期
   - 未找到 → 继续步骤 3
3. 扫描 knowledge/delta/ 下匹配 <change>-* 的目录：
   - 找到 → 读取其 delta.md Meta 获取 commit/date 信息
   - 未找到 → 报错停止（变更无归档记录）
4. 记录归档包路径：knowledge/delta/<change>-<commitId>-<date>/
```

### 步骤 2：恢复设计文件

```
1. 创建 .knowledge-work/<change>/ 目录（如不存在）
2. 从归档包复制设计文件：
   knowledge/delta/<包>/<change>/design.md   → .knowledge-work/<change>/design.md
   knowledge/delta/<包>/<change>/spec.md     → .knowledge-work/<change>/spec.md
   knowledge/delta/<包>/<change>/tasks.md    → .knowledge-work/<change>/tasks.md
   knowledge/delta/<包>/<change>/unit-task.md → .knowledge-work/<change>/unit-task.md
3. 验证 4 个文件全部复制成功
4. 向用户确认恢复结果
```

### 步骤 3：收集修正原因

```
1. 向用户请求团队 CR 反馈内容（必填）
2. 记录为 amendmentReason
3. 如果用户提供了 Gerrit 链接，记录为 amendmentRef
```

### 步骤 4：按 scope 更新流水线状态

#### scope = small

```
不修改 pipeline-state.json 的阶段状态（变更保持在 completedChanges 中）。
仅在 completedChanges 对应条目中追加 amendment 标记：
  "amendment": {
    "status": "in_progress",
    "scope": "small",
    "reason": "<amendmentReason>",
    "startedAt": "<时间戳>"
  }

输出提示：
  ✅ scope=small：变更保持已归档状态。
  请直接修改代码并 commit，完成后告诉我：
  1. 新的 commit hash
  2. 修改了什么（一句话）
```

#### scope = medium

```
1. 将变更从 completedChanges 移入 activeChange（或 parkedChanges 如已有活跃变更）：
   - activeChange: "<change>"
   - 变更数据：
     currentStage: 4
     stageStatus: "amendment"
     gates: { cr1: {status: "approved"}, build: {status: "pending"}, cr2: {status: "pending"} }
     amendment: {
       scope: "medium",
       reason: "<amendmentReason>",
       originalCommit: "<原 commit hash>",
       startedAt: "<时间戳>"
     }
   - history 追加：
     { stage: "amend", action: "amendment_start", at: "<时间戳>", 
       note: "scope=medium, reason: <amendmentReason>", originalStage: 8 }

2. 输出确认并说明下一步：
   ✅ 变更已移回流水线：阶段 4（Builder）
   scope=medium：方案不变，从代码实现开始。
   
   使用 /pipeline:next 启动 Builder，或直接说明需要改什么。
```

#### scope = large

```
1. 将变更从 completedChanges 移入 activeChange：
   - currentStage: 2
   - stageStatus: "amendment"
   - gates: { cr1: {status: "pending"}, build: {status: "pending"}, cr2: {status: "pending"} }
   - amendment: { scope: "large", reason: "<amendmentReason>", originalCommit: "<原 commit>", startedAt: "<时间戳>" }
   - history 追加：
     { stage: "amend", action: "amendment_start", at: "<时间戳>",
       note: "scope=large, reason: <amendmentReason>", originalStage: 8 }

2. 输出确认：
   ✅ 变更已移回流水线：阶段 2（Planner）
   scope=large：需要修订设计方案。
   
   使用 /pipeline:next 启动 Planner。
   注意：CR₁ 默认需要通过，如设计改动明确可用 /pipeline:next --force 跳过。
```

### 步骤 5：更新工作区记录

```
无论哪种 scope：
1. 更新 .knowledge-work/progress.md：
   - 在「当前状态」新增条目：
     ### <change> — 归档后修正（scope=<scope>）
     - 修正原因：<amendmentReason>
     - 原 commit：<hash>
     - 恢复自：<归档包路径>
     - 启动时间：<时间戳>

2. 更新 .knowledge-work/current-plan.md：
   - 新增条目：
     ### <change> — amendment (scope=<scope>)
     - 阶段：<scope 对应的起始阶段>
     - 下一步：<scope 对应的首个动作>
     - 阻塞项：无
```

### 步骤 6：scope=small 的完成处理

当 scope=small 且用户提供了新 commit hash 后，**重新执行 Archiver 流程**（审计 + delta 生成）：

```
1. 通过 Task tool 启动 Archiver 子 Agent，prompt 中指明：
   - 角色：Archiver（读取 knowledge/current/roles/archiver.md）
   - 变更名称：<change>
   - Amendment 模式：scope=small
   - 新 commit：<新 hash>
   - 原 commit：<原 hash>（供审计对比参考）

   Archiver 执行完整的步骤 0-4：
   - 步骤 0：归档前检查（制品已恢复，commit hash 用新的）
   - 步骤 1：一致性审计（基于新 commit diff）
   - 步骤 2：重新生成 delta.md（基于新代码事实）
   - 步骤 3：work-stash.md = 旧内容 + 追加「## 归档后修正」章节
   - 步骤 4：覆盖更新归档包（Amendment 覆盖模式）

2. Archiver 完成后，更新 pipeline-state.json completedChanges 对应条目：
   - commit: <新 hash>
   - amendment.status: "completed"
   - amendment.completedAt: <时间戳>

3. 清理 .knowledge-work/<change>/ 目录

4. 更新 progress.md：将该变更从「当前状态」移回「已完成」，追加 amendment 记录

5. 输出确认：
   ✅ 修正完成（scope=small）
   - 归档包已重新生成：<路径>
   - delta.md 基于新 commit 重新审计
   - work-stash.md 追加归档后修正章节
```

**与 scope=medium/large 的区别**：scope=small 不经过 Builder/Verifier/Committer（用户自行改代码+commit），
但 Archiver 审计和 delta 生成仍然完整执行，确保知识库更新指令与最新代码一致。

---

## Hook 点：Archiver 覆盖模式

当 scope=medium/large 的变更重新走到阶段 8（Archive）时，Archiver 需要识别这是 amendment 场景：

```
识别方式：
  - pipeline-state.json 中当前变更有 amendment 字段
  - knowledge/delta/ 下已存在 <change>-* 归档包

Archiver 行为差异（amendment 模式 vs 正常模式）：
  1. 读取旧归档包的 work-stash.md 保留原内容
  2. 删除旧归档包目录
  3. 创建新归档包目录（新 commitId + 新日期）
  4. delta.md 完全重新生成（基于新 commit diff）
  5. work-stash.md = 旧内容 + 追加「## 归档后修正」章节
  6. 设计文件用 .knowledge-work/<change>/ 中的最新版覆盖
  7. 归档报告中标注「Amendment 模式」
```

---

## Hook 点：/pipeline:next 阶段启动

当 amendment 变更通过 `/pipeline:next` 推进时，与正常流程一致，无需特殊处理。`amendment` 字段作为上下文传递给子 Agent，但不改变子 Agent 的执行逻辑。

唯一差异：Builder 的 prompt 中应追加上下文：
```
注意：本变更为归档后修正（amendment），修正原因：<reason>。
请在原有实现基础上修正，而非从头实现。
```

---

## Hook 点：Delta 已被 /archive-knowledge 合并

如果归档包已被合并删除（knowledge/delta/ 下无 <change>-* 目录）：

```
scope=small：
  无法执行（无归档包可更新）。
  → 产出补充 delta，命名为 <change>-amend-<newCommit>-<date>/
  → 补充 delta 只含修正相关的 Operations（基于新旧 commit 的 diff）

scope=medium/large：
  正常走流水线，最终 Archiver 产出新的归档包：
  → 命名为 <change>-amend-<newCommit>-<date>/（带 -amend- 标记区分首次归档）
  → delta.md 的 Meta 中 original_commit 指向首次归档的 commit
  → /archive-knowledge 合并时识别 -amend- 标记，视为增量更新
```

---

## 硬性约束

```
[MUST-DO]    必须从归档包恢复设计文件，不得从其他来源获取或凭记忆重建
[MUST-DO]    必须记录 amendment 原因（不允许空原因）
[MUST-DO]    scope=small 完成后必须更新归档包的 delta.md Meta 和 work-stash.md
[MUST-DO]    scope=medium/large 必须将变更重新纳入流水线管控（修改 pipeline-state.json）
[MUST-DO]    scope=medium/large 的代码修改必须经过 Verifier 验证
[DISALLOW]   禁止在无归档包且 scope=small 时执行（无法更新不存在的归档包）
[DISALLOW]   禁止跳过 amendment 原因记录
[DISALLOW]   禁止在 scope=small 时修改 pipeline-state.json 的阶段状态（变更保持 completed）
[ASSERT-DISALLOW] scope=medium/large 时，禁止跳过 Verifier 直接 commit
```

---

## 完成标准

### scope=small 完成标准

```
[ ] 归档包已定位且设计文件已恢复
[ ] 用户已提供新 commit hash 和修正描述
[ ] delta.md Meta 已追加 amendment 字段（amendment_commit/date/reason/original_commit）
[ ] work-stash.md 已追加「## 归档后修正」章节（含 6 个字段）
[ ] pipeline-state.json completedChanges 已更新 commit + amendment.status
[ ] .knowledge-work/<change>/ 已清理
[ ] progress.md 已更新
```

### scope=medium/large 完成标准

```
[ ] 归档包已定位且设计文件已恢复到 .knowledge-work/<change>/
[ ] pipeline-state.json 已将变更移回流水线（含 amendment 字段）
[ ] history 已追加 amendment_start 记录
[ ] progress.md 和 current-plan.md 已更新
[ ] 后续流程按正常阶段推进（由 /pipeline:next 接管）
```

---

## 返回报告

执行完成后必须输出：

```
## Amendment 启动报告

- 变更：<change>
- Scope：<scope>
- 修正原因：<reason>
- 原 commit：<hash>
- 归档包位置：<path>
- 设计文件恢复：✅ 4 个文件
- 流水线状态：<保持 completed / 移回阶段 N>
- 下一步：<具体指引>
```
