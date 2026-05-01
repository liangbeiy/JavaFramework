---
name: "archive-knowledge"
description: "合并知识库 Delta 至 current/。执行完整 6 步协议：发现 delta → 创建合并前快照 → 逐个应用 → 更新版本 → 清理。Invoke when user asks to merge/apply deltas, 合并delta, 合并知识库, or runs /archive-knowledge."
---

# archive-knowledge

版本负责人统一执行知识库合并 — 批量应用 delta、创建快照、更新知识库、清理已应用的 delta。

## 触发条件

用户输入 `/archive-knowledge` 时激活。任何人可执行，但设计上由版本负责人统一操作。

---

## 前置条件

```
1. knowledge/delta/ 目录存在且非空（至少有 1 个待合并的归档包）
2. knowledge/CURRENT.md 存在（获取当前版本号）
3. knowledge/snapshots/_index.md 存在（获取快照列表）
```

如果 `knowledge/delta/` 不存在或为空，输出后停止：
```
无待合并的 Delta。knowledge/delta/ 目录为空。
```

---

## 执行协议

**6 个步骤必须按序执行，不可调换顺序。**

### 步骤 1：发现与展示待合并 Delta

```
1. 扫描 knowledge/delta/ 下所有子目录
2. 对每个归档包，读取其 delta.md 的 ## Meta 部分，提取：
   - change name
   - commit hash
   - date
   - knowledge_baseline（基于哪个版本产出的 delta）
3. 按 datetime 升序排列
4. 展示待合并列表，请求用户确认：

   ## 待合并 Delta（共 N 个）

   | # | 变更名称 | Commit | 日期 | 基线版本 | Operations |
   |---|---------|--------|------|---------|-----------|
   | 1 | foo-feature | abc12345 | 2026-04-28 | v1.1 | 3 ops |
   | 2 | bar-bugfix | def67890 | 2026-04-29 | v1.1 | 2 ops |

   确认合并以上 Delta？（将创建快照 v1.2）

5. 等待用户确认后继续
```

**基线版本校验**：
```
如果某个 delta 的 knowledge_baseline 与当前 CURRENT.md 的 version 不一致：
  - 落后 1 个版本（如 delta 基于 v1.0，当前已是 v1.1）→ 警告但允许继续
  - 落后 2+ 个版本 → 警告并建议用户确认是否仍然适用
  - 超前于当前版本 → 错误，阻断，报告异常
```

### 步骤 2：创建快照

```
1. 读取 knowledge/CURRENT.md — 获取 current_version
2. 计算新版本号：current_version patch +1（如 v1.1 → v1.2）
3. 新建快照目录：knowledge/snapshots/<日期>_<当前版本号>/
   注意：快照保存的是合并前的状态，所以用当前版本号命名
4. 拷贝以下内容到快照目录：
   - _index.md
   - architecture.md
   - constitution.md
   - glossary.md
   - modules/ 目录（完整）
   - cross-cutting/ 目录（完整）
   - 不拷贝：roles/ / style/ / router/ / delta/
5. 更新 knowledge/snapshots/_index.md — 追加新快照记录行：
   | <日期>_<版本号> | <日期> | <状态> | <模块数> | Pre-merge snapshot for N deltas |
```

**[ASSERT-DISALLOW] 快照未完成前，禁止修改 knowledge/current/ 下任何文件。**

### 步骤 3：快照数量管理

```
1. 检查 knowledge/snapshots/ 下快照目录数量（排除 _index.md）
2. 如超过 5 份：
   a. 确定最旧的快照目录
   b. 向用户报告：即将删除快照 <名称>（最旧）
   c. 用户确认后删除该目录
   d. 同步从 snapshots/_index.md 中移除对应记录行
3. 重复直到快照数量 ≤ 5
```

### 步骤 4：逐个应用 Delta

按步骤 1 排好的时间顺序，逐个处理每个 delta.md：

```
对每个 delta.md：
  1. 读取 ## Operations 部分
  2. 按 OP 编号顺序逐个应用：

     APPEND file @ "section"：
       - 在目标文件中定位 section 标题
       - 找到该章节的末尾（下一个同级或更高级标题之前）
       - 在末尾追加 ```knowledge ``` 块中的内容
       - 追加时保留一个空行分隔

     REPLACE file @ "section"：
       - 在目标文件中定位 section 标题
       - 确定章节范围（从标题行到下一个同级标题之前）
       - 用 ```knowledge ``` 块中的内容替换（保留标题行本身）

     CREATE file：
       - 确认目标文件不存在
       - 创建文件，写入 ```knowledge ``` 块中的内容

     ADD_SECTION file @ after "section"：
       - 在目标文件中定位 after section
       - 找到该章节的末尾
       - 在末尾插入 ```knowledge ``` 块中的完整新章节（含标题）

     DEPRECATE file @ "section"：
       - 在目标文件中定位 section 标题
       - 解析 ```knowledge ``` 块中的 reason/alternative/since 字段
       - 在标题行之后、正文首行之前插入废弃标记：
         > ⚠️ **DEPRECATED** since <since>
         > 原因：<reason>
         > 替代方案：<alternative>
       - 原有内容保留不动

  3. 处理 ## Known Issues 部分：
     - 对每条 issue，读取其 写入目标 字段
     - 在目标文件中定位或创建「## ⚠️ 已知问题」章节
     - 追加 issue 条目，格式：
       - [<change>/<日期>] <问题描述> — 影响：<范围> — 建议：<修复方向>
```

**冲突检测与自动解决**：

```
Level 0 — 无冲突（静默合并）：
  • 不同文件的操作 → 直接应用
  • 同文件不同 Section → 直接应用
  • 同文件同 Section 都是 APPEND → 按时间顺序拼接

Level 1 — 低风险（自动合并 + 标记 review）：
  • 同文件同 Section 混合操作（REPLACE + APPEND）
    → 先应用 REPLACE（时间早的），再应用 APPEND（时间晚的）
    → 合并报告中标注 ⚠️ 建议人工复核此章节
  • ADD_SECTION 的位置恰好是另一个 delta REPLACE 的 section
    → 先 REPLACE，再 ADD_SECTION（新章节在替换后的章节之后）
    → 标注 ⚠️

Level 2 — 真冲突（阻断，报告人工决策）：
  • 同文件同 Section 都是 REPLACE → 内容矛盾
    → 展示两个 delta 的内容，请求人工选择：
      a) 用 delta A 的内容
      b) 用 delta B 的内容
      c) 手动合并（用户提供合并后内容）
  • 两个 delta 都 CREATE 同一个文件
    → 同上处理
```

**Section 定位失败处理（三级 Fallback）**：

```
如果 delta 中指定的 Section 标题在目标文件中不存在：

  Level 1 失败 → 进入 Level 2（上下文锚点 Fallback）：
  1. 读取该 OP 的 <!-- context: after "X" | before "Y" --> 注释
  2. 在文件中定位 X 和 Y 标题
  3. 在 X 和 Y 之间查找同级标题：
     - 找到唯一标题 → 使用，报告标注 ⚠️ "标题已变化：原 <旧> → 现 <新>"
     - X 或 Y 也不存在 → 进入 Level 3
     - 找到多个标题 → 进入 Level 3

  Level 2 也失败 → Level 3（阻断，人工决策）：
  1. 展示目标文件的完整标题结构
  2. 展示 OP 原始定位信息 + context 注释
  3. 请求用户指定正确的 Section

  如果 OP 没有 context 注释（兼容旧版 delta）：
  → 尝试模糊匹配（忽略大小写、忽略标题级别差异）
  → 唯一匹配 → 使用，标注 ⚠️
  → 否则 → 阻断
```

### 步骤 5：更新版本元数据

```
1. 更新 knowledge/CURRENT.md：
   - current_version: <新版本号>（patch +1）
   - generated_at: <今天日期>
   - status: draft
   - snapshot_ref: snapshots/<日期>_<旧版本号>/
   - modules_count: <当前模块数>

2. 更新 knowledge/current/_index.md 版本历史表：
   - 追加一行：| <新版本号> | <日期> | <模块数> | <文档数> | <本次合并摘要> |
   - 摘要格式：apply N deltas: <change1>, <change2>, ...
```

### 步骤 6：清理已应用的 Delta

```
1. 删除 knowledge/delta/ 下所有已成功应用的归档包目录
2. 如有因冲突未能应用的 delta → 保留在 knowledge/delta/ 中，不删除
3. 输出清理确认
```

---

## 返回合并报告

必须包含 6 个部分：

```
1. 合并概览
   - 合并了 N 个 delta
   - 版本变化：v1.1 → v1.2
   - 涉及变更：<change1>, <change2>, ...

2. 快照信息
   - 新建快照：<快照名称>
   - 是否删除旧快照（如有，说明删除了哪个）

3. 应用详情（逐个 delta）
   - <change1>：N 个操作全部成功
   - <change2>：M 个操作成功，K 个标注 review
   - ...

4. 冲突处理记录（如有）
   - Level 1 自动合并：列出文件和章节，标注 ⚠️
   - Level 2 人工决策：列出决策内容和结果

5. Known Issues 汇总
   - 共沉淀 N 条已知问题
   - 写入位置列表

6. 清理确认
   - 已删除：N 个归档包目录
   - 保留（冲突未解决）：列表（如有）
```

---

## 硬性约束

```
[ASSERT-DISALLOW] 快照未完成时禁止修改 knowledge/current/
[ASSERT-DISALLOW] 禁止跳过用户确认直接合并
[ASSERT-DISALLOW] Level 2 冲突未经人工决策时禁止继续
[ASSERT-DISALLOW] 禁止删除未成功应用的 delta（保留供后续处理）
[ASSERT-DISALLOW] 禁止修改源码文件
[ASSERT-DISALLOW] 禁止执行 git commit（合并后的 commit 由用户自行决定）
[DISALLOW]        禁止修改 delta 包中的文件（只读取、应用、删除整个目录）
[DISALLOW]        禁止静默删除快照（必须告知用户）
[DISALLOW]        Section 定位失败时禁止静默跳过（必须报告）
[MUST-DO]         所有知识库修改必须在合并报告中逐条列明
[MUST-DO]         Level 1 自动合并必须在报告中标注 ⚠️ 供人工复核
```

---

## 完成标准

执行者（或 Orchestrator）逐项验证：

```
--- 步骤 1 验证 ---
[ ] 已展示待合并列表并获得用户确认
[ ] 基线版本校验已执行（落后版本已警告）

--- 步骤 2 验证 ---
[ ] 新快照目录已创建：knowledge/snapshots/<日期>_<版本>/
[ ] 快照包含：_index.md / architecture.md / constitution.md / glossary.md / modules/ / cross-cutting/
[ ] snapshots/_index.md 已追加新记录

--- 步骤 3 验证 ---
[ ] 快照目录总数 ≤ 5
[ ] 如删除旧快照，已告知用户

--- 步骤 4 验证 ---
[ ] 所有 Level 0 操作已静默成功应用
[ ] Level 1 操作已应用且报告中标注 ⚠️
[ ] Level 2 冲突已经人工决策处理（或对应 delta 保留未删除）
[ ] Section 定位全部成功（或定位失败的已报告人工处理）
[ ] Known Issues 已写入对应文件

--- 步骤 5 验证 ---
[ ] CURRENT.md 版本号已递增
[ ] CURRENT.md generated_at 为今天
[ ] _index.md 版本历史表已追加记录

--- 步骤 6 验证 ---
[ ] 成功应用的 delta 归档包已删除
[ ] 未成功的 delta 保留在 knowledge/delta/ 中
[ ] 合并报告包含全部 6 个部分且内容完整
```
