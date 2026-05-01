---
name: "openspec-archive-change"
description: "阶段 8 归档。一致性审计 → 生成 Delta → 组装归档包 → 清理工作区。Invoke when user wants to archive a completed change."
---

# openspec-archive-change

阶段 8 — 归档。一致性审计 → 生成知识库 Delta → 保存决策上下文 → 组装归档包 → 清理工作区。

## 触发条件

Orchestrator 发起阶段 8 子 Agent 时自动加载。前置条件：阶段 7 Committer 已完成 git commit。

---

## 核心变化（与旧版 Archiver 的区别）

```
旧版：快照 → 直接修改 knowledge/current/ → 清理
新版：审计 → 生成 delta → 生成 work-stash → 打包到 knowledge/delta/ → 清理

Archiver 不再直接修改 knowledge/current/，不再创建快照。
知识库的实际修改由版本负责人通过 /archive-knowledge 统一执行。
```

---

## 执行协议

**5 个步骤必须按序执行，不可调换顺序。**

### 步骤 0：归档前检查

归档前必须验证变更的完备性：

```
1. 制品完成度检查：
   - 确认 .knowledge-work/<change>/ 下以下文件全部存在：
     • design.md
     • spec.md
     • tasks.md
     • unit-task.md
   - 如有制品缺失 → 立即停止，向 Orchestrator 报告

2. Task 完成度检查：
   - 读取 tasks.md，统计 [ ]（未完成）和 [x]（已完成）
   - 如有未完成 task → 向 Orchestrator 报告数量，由其确认是否继续

3. Commit 存在性检查：
   - 读取 .knowledge-work/progress.md，确认该变更有 commit hash 记录
   - 无 commit → 禁止归档，立即停止
```

### 步骤 1：一致性审计

**目的**：验证代码实现与设计文档是否一致，确保后续生成的 delta 基于事实。

```
输入：
  - commit diff（git show <commit-hash> --stat + git diff <commit-hash>~1 <commit-hash>）
  - 源码（按 diff 涉及的文件 Read）
  - design.md（方案选型、架构决策、接口设计）
  - spec.md（功能规格：WHEN/THEN 场景）
  - tasks.md（任务分解、每个 task 的预期产出）
  - unit-task.md（测试用例设计）

对比维度：
  1. design 决策 vs 代码实现
     - 方案选型是否一致（如 design 说用策略模式，代码是否用了）
     - 接口设计是否一致（类名/方法签名/继承关系）

  2. spec 场景 vs 代码覆盖
     - spec 第 2 章列出的每个 WHEN/THEN 场景
     - 代码中是否有对应的逻辑分支

  3. tasks 预期产出 vs 实际产出
     - 每个 task 声明要创建/修改的文件
     - commit diff 中是否确实包含

  4. unit-task 测试设计 vs 实际测试
     - unit-task 要求的测试类/方法
     - 测试代码中是否存在且覆盖了 core/boundary/unexpected 三类场景

产出：一致性报告
  - MATCH：文档与代码一致的部分（列出要点）
  - DRIFT：文档与代码不一致的部分，每条包含：
    • 偏差描述
    • 文档说什么
    • 代码实际做了什么
    • 严重度：cosmetic / material / critical
      - cosmetic：命名差异、注释缺失、无实质影响
      - material：实现方式不同但功能等价、参数个数变化
      - critical：功能缺失、逻辑相反、架构违反

决策门禁：
  - 无 DRIFT 或仅 cosmetic → 继续步骤 2
  - 有 material DRIFT → 继续，但 delta 以代码为准，报告中标注偏差
  - 有 critical DRIFT → 阻断，向 Orchestrator 报告，等待人工决策
    人工决策选项：
    a) 以代码为准，继续归档（接受偏差）
    b) 打回修复代码/文档后重新归档
```

### 步骤 2：生成 delta.md

**目的**：生成结构化的知识库更新指令文件。

**信息来源优先级**：
```
第一信源：代码事实（commit diff + 源码）
第二信源：审计通过的文档（辅助理解设计意图）
如有 DRIFT：以代码为准，在 delta 中反映代码实际行为
```

**Delta 文件格式**：

```markdown
# Knowledge Delta: <change_name>

## Meta
| Key | Value |
|-----|-------|
| change | <change_name> |
| commit | <commit_hash> |
| date | <YYYY-MM-DD> |
| author | <developer> |
| files_changed | <N> |
| lines | +<added>/-<removed> |
| knowledge_baseline | <当前 CURRENT.md 中的 version> |

## Consistency Audit Summary
| Dimension | Result | Intentional | Notes |
|-----------|--------|-------------|-------|
| design vs code | MATCH/DRIFT(N) | M intentional | <简要说明> |
| spec vs code | MATCH/DRIFT(N) | M intentional | <简要说明> |
| tasks vs code | MATCH/DRIFT(N) | M intentional | <简要说明> |
| unit-task vs tests | MATCH/DRIFT(N) | M intentional | <简要说明> |

## Operations

### OP-1: APPEND modules/Foo/overview.md @ "## 核心类"

```knowledge
- `FooManager`：负责 xxx，继承 BaseManager<T>
- `FooHelper`：工具类，提供 yyy 能力
```

### OP-2: REPLACE cross-cutting/调试工具.md @ "## 性能采集指标"

```knowledge
（替换后的完整章节内容，从该标题到下一个同级标题之前）
```

### OP-3: CREATE modules/NewModule/overview.md

```knowledge
（新文件完整内容）
```

### OP-4: ADD_SECTION modules/Foo/business.md @ after "## 现有流程"

```knowledge
## 新增流程

（新章节完整内容）
```

## Known Issues

### ISSUE-1
- 问题：<问题描述>
- 发现：<change_name>/<日期>
- 影响：<范围>
- 建议：<修复方向>
- 写入目标：<target_file> @ "## ⚠️ 已知问题"

## Lessons Learned
- <经验条目 1>
- <经验条目 2>
```

**格式规则**：

```
1. Operations 部分：
   - 每个操作用 H3 标题，格式固定：### OP-N: <ACTION> <file> @ "<section>"
   - ACTION 允许 5 种：APPEND / REPLACE / CREATE / ADD_SECTION / DEPRECATE
   - 内容用 fenced code block ```knowledge ``` 包裹（避免与 delta 自身格式冲突）
   - Section 定位用完整的 Markdown 标题文本（含 ## 前缀）
   - 每个需要 Section 定位的 OP（除 CREATE 外），H3 标题行之后必须紧跟 context 注释：
     `<!-- context: after "<前一个同级标题>" | before "<后一个同级标题>" -->`
     （Archiver 生成时从目标文件中读取前后兄弟标题填入）

2. ACTION 语义：
   - APPEND：在指定章节末尾（下一个同级标题之前）追加内容
   - REPLACE：替换整个章节（从标题行到下一个同级标题之前的全部内容）
   - CREATE：创建新文件（不需要 @ section）
   - ADD_SECTION：在指定章节之后新增一个完整的新章节
   - DEPRECATE：标记指定章节为已废弃（插入废弃标记，保留原内容）

3. Known Issues 部分：
   - 每条必须包含四要素：问题、发现场景、影响、建议
   - 必须指定 写入目标（哪个文件的哪个章节）

4. 禁止的操作：
   - 行级插入
   - 段落级替换（必须以 section 为单位）
   - DELETE 操作（知识库只增不删）
```

**自检**（生成后立即执行）：

```
[ ] 每个 OP 的 H3 标题格式是否符合 "### OP-N: ACTION file @ section"
[ ] ACTION 是否在允许的 5 种之内（APPEND/REPLACE/CREATE/ADD_SECTION/DEPRECATE）
[ ] 内容是否用 ```knowledge ``` 包裹
[ ] Section 标题在目标文件中是否真实存在（APPEND/REPLACE/DEPRECATE 时）
[ ] CREATE 的文件是否确实不存在
[ ] ADD_SECTION 的 after section 是否存在
[ ] 每个需要定位的 OP（除 CREATE）是否有 context 注释（after/before 标题）
[ ] context 中的 after/before 标题在目标文件中真实存在
[ ] DEPRECATE 操作的 knowledge block 包含 reason/alternative/since 三个字段
[ ] Known Issues 每条是否四要素齐全
[ ] Meta 中的 knowledge_baseline 是否与 CURRENT.md 一致
```

### 步骤 3：生成 work-stash.md

**目的**：保留完整的变更决策记忆，供未来回溯。

**信息来源**：

| 内容 | 来源 |
|------|------|
| 用户决策记录 | progress.md「关键决策和上下文」中本变更相关条目 |
| CR 反馈与响应 | pipeline-state.json history 中的 note 字段 |
| 实现偏差 | 步骤 1 一致性审计的产出 |
| 被否决的方案 | design.md 的 Decisions / Alternatives 章节 |
| 遗留与后续 | Known Issues + 审计中发现的未覆盖项 |

**Work-Stash 文件格式**：

```markdown
# Work Stash: <change_name>

## 变更元信息
- change: <change_name>
- commit: <commit_hash>
- date: <YYYY-MM-DD>
- author: <developer>
- files: <N> changed, +<added>/-<removed>
- 知识库基线: <version>
- 流水线耗时: <创建到归档的时间跨度>

## 用户决策记录（按时间序）
1. [MM-DD] <决策内容>
2. [MM-DD] <决策内容>
...

## CR 反馈与响应
- CR1: <结论> — <具体反馈和响应动作>
- CR2: <结论> — <具体反馈和响应动作>

## 一致性审计结果
- 整体结论：<PASS / PASS WITH DRIFT>
- DRIFT 列表（如有）：
  - [material] <偏差描述>：文档说 X，代码实际 Y，以代码为准

## 被否决的方案
- 方案 X → 否决原因：<原因>
- 方案 Y → 否决原因：<原因>

## 遗留与后续
- <遗留事项 1>
- <遗留事项 2>
```

### 步骤 4：组装归档包

**目的**：将所有产出物写入 `knowledge/delta/` 目录。

```
目标路径：knowledge/delta/<change>-<commitId_short>-<YYYYMMDD>/

目录结构：
  knowledge/delta/<change>-<commitId_short>-<YYYYMMDD>/
  ├── delta.md              ← 步骤 2 产出
  ├── work-stash.md         ← 步骤 3 产出
  └── <change>/             ← 设计文件快照（从 .knowledge-work/<change>/ 复制）
      ├── design.md
      ├── spec.md
      ├── tasks.md
      └── unit-task.md

命名规则：
  - commitId_short：commit hash 前 8 位
  - YYYYMMDD：归档日期
  - 示例：black-screen-report-47f44986-20260428/
```

**执行动作**：
```
1. 检查 knowledge/delta/ 下是否已存在该变更的归档包（<change>-* 目录）
   - 不存在 → 正常流程（首次归档）
   - 已存在 → 进入 Amendment 覆盖模式（见下方）
2. 创建目标目录
3. 写入 delta.md
4. 写入 work-stash.md
5. 创建 <change>/ 子目录
6. 从 .knowledge-work/<change>/ 复制 design.md / spec.md / tasks.md / unit-task.md
7. 验证归档包完整性：确认 6 个文件全部写入成功
```

**Amendment 覆盖模式**（归档包已存在时）：

当 `knowledge/delta/` 下已存在 `<change>-*` 目录时，说明这是一次归档后修正（团队 CR 打回后的 `/pipeline:amend` 场景）。

```
执行步骤：
1. 读取旧归档包中的 work-stash.md 保留原有内容
2. 删除旧归档包目录（<change>-<oldCommit>-<oldDate>/）
3. 创建新归档包目录（<change>-<newCommit>-<newDate>/）
4. 写入新的 delta.md（基于新 commit diff 完全重新生成）
5. 写入新的 work-stash.md = 旧内容 + 追加「## 归档后修正」章节：
   ## 归档后修正
   - 修正原因：<pipeline history 中 amendment 的 note>
   - 原 commit：<旧 commit hash>
   - 新 commit：<新 commit hash>
   - 修正日期：<YYYY-MM-DD>
   - scope：<small/medium/large>
   - 变更描述：<本次修正具体改了什么>
6. 复制设计文件（用最新版本）
7. 验证归档包完整性
```

### 步骤 5：清理工作区

```
1. 删除 .knowledge-work/<change>/ 目录下全部文件（及目录本身）
2. 更新 .knowledge-work/pipeline-state.json：
   - 将变更从 activeChange/parkedChanges 移入 completedChanges
   - completedChanges 条目必须包含 metrics 统计摘要（从 history 数组计算）：
     {
       "name": "<change>",
       "completedAt": "<今天日期>",
       "commit": "<commit hash>",
       "stats": "<N files, +added/-removed>",
       "metrics": {
         "duration_days": <stage 1 的 at 到今天的天数>,
         "started_at": "<stage 1 的 at>",
         "cr1_result": "<approved/rejected/skipped>",
         "cr2_result": "<approved/rejected/skipped>",
         "build_verify_rounds": <stage 4→5 循环次数>,
         "total_rejections": <history 中 action="rejected" 的计数>,
         "amendment_count": <amendment 次数，首次归档为 0>
       }
     }
3. 更新 .knowledge-work/progress.md：
   - 将变更从「当前状态」移至「已完成」
   - 记录：完成日期、commit hash、修改文件数和行数变化
   - 在「关键决策和上下文」中保留本变更最重要的 1-2 条决策摘要
4. 更新 .knowledge-work/current-plan.md：移除已归档变更的计划条目
```

---

## 返回归档报告

必须包含 5 个部分（任一不可省略）：

```
1. 归档前检查结果
   - 制品完成度：全部存在 / 缺失列表
   - Task 完成度：N/M 完成
   - Commit：<hash>

2. 一致性审计摘要
   - 四维度对比结果（MATCH/DRIFT）
   - DRIFT 详情（如有）
   - 结论：PASS / PASS WITH DRIFT / BLOCKED

3. Delta 文件摘要
   - 操作数量：N 个 operations
   - 涉及文件：列表
   - Known Issues 数量

4. 归档包确认
   - 路径：knowledge/delta/<完整目录名>/
   - 文件清单：delta.md / work-stash.md / <change>/(4 files)

5. 工作区清理确认
   - 已删除：.knowledge-work/<change>/
   - 已更新：progress.md / current-plan.md
```

---

## 硬性约束

```
[ASSERT-DISALLOW] 变更未完成提交（无 commit hash）时禁止归档
[ASSERT-DISALLOW] 禁止修改 knowledge/current/ 下任何文件
[ASSERT-DISALLOW] 禁止创建快照（快照由 /archive-knowledge 负责）
[ASSERT-DISALLOW] 禁止删除 progress.md 或 current-plan.md 本身
[ASSERT-DISALLOW] 禁止删除其他变更的 .knowledge-work/ 文件
[ASSERT-DISALLOW] critical DRIFT 未经人工确认时禁止继续生成 delta
[DISALLOW]        禁止修改源码或执行构建/提交命令
[DISALLOW]        禁止通过 task_id 复用其他阶段的子 Agent 会话
[DISALLOW]        delta.md 中禁止使用 APPEND/REPLACE/CREATE/ADD_SECTION 之外的操作类型
[MUST-DO]         delta 生成后必须执行自检清单
[MUST-DO]         以代码事实为第一信源，文档为辅助
[MUST-DO]         归档包中必须包含完整的设计文件副本
```

---

## 完成标准

Orchestrator 逐项验证，任一项 FAIL 必须打回重做：

```
--- 步骤 0 验证 ---
[ ] 制品文件齐全：design.md / spec.md / tasks.md / unit-task.md
[ ] progress.md 中该变更有 commit hash 记录

--- 步骤 1 验证 ---
[ ] 一致性审计已执行，报告包含四维度对比结果
[ ] 如有 critical DRIFT，已阻断并报告（而非跳过）
[ ] 如有 material DRIFT，delta 中以代码为准且报告中标注

--- 步骤 2 验证 ---
[ ] delta.md 存在于归档包中
[ ] delta.md 格式自检清单全部通过
[ ] Operations 中每个操作的目标文件确实存在（CREATE 除外）
[ ] Operations 中每个 Section 定位在目标文件中真实存在
[ ] Known Issues 每条四要素齐全（问题/发现/影响/建议）

--- 步骤 3 验证 ---
[ ] work-stash.md 存在于归档包中
[ ] 包含用户决策记录（至少 1 条）
[ ] 包含一致性审计结果摘要
[ ] 包含 CR 反馈记录

--- 步骤 4 验证 ---
[ ] 归档包目录已创建：knowledge/delta/<change>-<commitId>-<date>/
[ ] 归档包包含 6 个文件：delta.md + work-stash.md + 4 个设计文件
[ ] 目录命名格式正确

--- 步骤 5 验证 ---
[ ] .knowledge-work/<change>/ 已清理
[ ] progress.md 中该变更已移至「已完成」，含日期和 commit hash
[ ] current-plan.md 中已移除该变更

--- 归档报告完整性 ---
[ ] 报告包含全部 5 个部分
[ ] 每个部分有实质内容（非空或有明确的「无」说明）
```
