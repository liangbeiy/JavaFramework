# Delta 文件格式规范

本文档定义 `knowledge/delta/` 下归档包中 `delta.md` 的标准格式。所有 Archiver 生成的 delta 必须严格遵循本规范，`/archive-knowledge` 按本规范解析和应用。

---

## 归档包目录结构

```
knowledge/delta/<change>-<YYYYMMDD>/
├── delta.md              ← 知识库更新指令（本文档规范）
├── work-stash.md         ← 变更决策记忆
└── <change>/             ← 设计文件副本
    ├── design.md
    ├── spec.md
    ├── tasks.md
    └── unit-task.md
```

命名示例：`black-screen-report-20260428/`

---

## delta.md 完整格式

```markdown
# Knowledge Delta: <change_name>

## Meta
| Key | Value |
|-----|-------|
| change | <change_name> |
| commit | <commit_hash_full> |
| date | <YYYY-MM-DD> |
| author | <developer> |
| files_changed | <N> |
| lines | +<added>/-<removed> |
| knowledge_baseline | <version, e.g. v1.1> |

## Consistency Audit Summary
| Dimension | Result | Notes |
|-----------|--------|-------|
| design vs code | MATCH/DRIFT(severity) | <简要说明> |
| spec vs code | MATCH/DRIFT(severity) | <简要说明> |
| tasks vs code | MATCH/DRIFT(severity) | <简要说明> |
| unit-task vs tests | MATCH/DRIFT(severity) | <简要说明> |

## Operations

### OP-1: APPEND <file_path> @ "<section_title>"

` ` `knowledge
<追加的内容>
` ` `

### OP-2: REPLACE <file_path> @ "<section_title>"

` ` `knowledge
<替换后的完整章节内容，不含标题行>
` ` `

### OP-3: CREATE <file_path>

` ` `knowledge
<新文件完整内容>
` ` `

### OP-4: ADD_SECTION <file_path> @ after "<section_title>"

` ` `knowledge
<新章节完整内容，含标题行>
` ` `

## Known Issues

### ISSUE-1
- 问题：<问题描述>
- 发现：<change_name>/<YYYY-MM-DD>
- 影响：<范围>
- 建议：<修复方向>
- 写入目标：<file_path> @ "## ⚠️ 已知问题"

## Lessons Learned
- <经验条目>
```

> 注意：上方 fenced block 为示意，实际 delta.md 中使用三个反引号 + `knowledge` 标识符。

---

## 操作类型定义

### APPEND

**语义**：在目标文件的指定章节末尾追加内容。

```
定位规则：
1. 在文件中找到 section_title 标题行（精确匹配，含 ## 前缀）
2. 找到该章节的末尾（下一个同级或更高级标题之前、或文件末尾）
3. 在末尾追加内容（前面保留一个空行分隔）

示例：
  文件中有 "## 核心类" 章节，在其末尾追加新的类说明
```

**H3 标题格式**：`### OP-N: APPEND <file_path> @ "<section_title>"`

### REPLACE

**语义**：替换目标文件指定章节的全部内容（保留标题行本身）。

```
定位规则：
1. 在文件中找到 section_title 标题行
2. 确定章节范围：从标题行的下一行开始，到下一个同级标题之前
3. 用 knowledge block 中的内容替换这个范围

注意：标题行本身保留不变，只替换内容
```

**H3 标题格式**：`### OP-N: REPLACE <file_path> @ "<section_title>"`

### CREATE

**语义**：创建一个新文件。

```
规则：
1. 确认目标路径的文件不存在
2. 如有必要，创建中间目录
3. 写入 knowledge block 中的完整内容

如果文件已存在 → 视为冲突，/archive-knowledge 阻断报告
```

**H3 标题格式**：`### OP-N: CREATE <file_path>`（无 @ section）

### ADD_SECTION

**语义**：在目标文件的指定章节之后新增一个完整的新章节。

```
定位规则：
1. 在文件中找到 after section_title 标题行
2. 找到该章节的末尾
3. 在末尾插入 knowledge block 中的完整新章节（含新标题行）

注意：knowledge block 内容必须包含新章节的标题行
```

**H3 标题格式**：`### OP-N: ADD_SECTION <file_path> @ after "<section_title>"`

### DEPRECATE

**语义**：将目标文件指定章节标记为已废弃（不删除原有内容，插入废弃标记）。

```
适用场景：
  - 某个类/方法已被删除或废弃
  - 某个流程已被新流程替代
  - 某个配置项已失效

规则：
1. 在目标文件中找到 section_title 标题行
2. 在标题行之后、正文首行之前插入废弃标记块
3. 原有内容保留不动（供历史参考）

废弃标记格式（由 /archive-knowledge 应用时插入）：
  > ⚠️ **DEPRECATED** since <change>(<date>)
  > 原因：<reason>
  > 替代方案：<alternative>（如有）
```

**H3 标题格式**：`### OP-N: DEPRECATE <file_path> @ "<section_title>"`

**knowledge block 格式**：
```markdown
```knowledge
reason: <废弃原因>
alternative: <替代方案描述或路径>（如无则写 "无"）
since: <change_name> (<YYYY-MM-DD>)
` ` `
```

---

## Section 定位规则

### 精确匹配

Section title 必须与目标文件中的 Markdown 标题**精确匹配**（含 `#` 前缀和空格）。

```
正确：@ "## 核心类"        → 匹配文件中的 ## 核心类
错误：@ "核心类"           → 缺少 ## 前缀
错误：@ "### 核心类"       → 级别不匹配
```

### 上下文锚点（Context 注释）

每个需要 Section 定位的 OP（APPEND/REPLACE/ADD_SECTION/DEPRECATE），**必须**在 H3 标题行之后紧跟一行 context 注释，记录目标 Section 的前后兄弟标题：

```markdown
### OP-1: APPEND `调试工具.md` @ "### 8.4 性能数据采集体系"
<!-- context: after "### 8.3 Cocos 引擎性能参数" | before "### 9.1 GameEngineUtils 引擎选择逻辑" -->
```

**格式规则**：
- 固定前缀 `<!-- context: `，固定后缀 ` -->`
- `after` 为目标 Section 的前一个同级标题（如果是首个章节，写 `after "文件开头"`）
- `before` 为目标 Section 的后一个同级标题（如果是最后一个章节，写 `before "文件末尾"`）
- after 和 before 之间用 ` | ` 分隔
- CREATE 操作不需要 context（无 Section 定位）

**作用**：当精确标题匹配失败时，`/archive-knowledge` 使用 context 中的前后标题进行 fallback 定位。

### 同级判定

章节范围的"下一个同级标题"按 `#` 数量判定：
- `##` 章节的范围到下一个 `##` 或 `#` 之前
- `###` 章节的范围到下一个 `###` 或 `##` 或 `#` 之前
- 如果到文件末尾都没有同级标题，则范围延伸到文件末尾

### 定位失败处理（三级 Fallback）

由 `/archive-knowledge` 在应用时处理：

```
Level 1：精确匹配
  → 在文件中查找完全匹配的标题文本
  → 成功则使用

Level 2：上下文锚点 Fallback
  → 读取 OP 的 context 注释
  → 在文件中定位 after 和 before 标题
  → 在两者之间查找唯一的同级标题
  → 找到唯一匹配 → 使用，报告标注 ⚠️ "标题已变化：原 X → 现 Y"
  → 找到多个 → 进入 Level 3

Level 3：阻断，人工决策
  → 展示目标文件的标题结构
  → 展示 context 中的 after/before 信息
  → 请求用户指定正确的 Section
```

---

## 冲突检测规则

当同一批次中多个 delta 操作同一文件时：

| Delta A 操作 | Delta B 操作 | 同一 Section? | 冲突级别 |
|-------------|-------------|:---:|:---:|
| APPEND | APPEND | 是 | Level 0（按时间拼接） |
| APPEND | APPEND | 否 | Level 0 |
| APPEND | REPLACE | 是 | Level 1（先 REPLACE 再 APPEND） |
| REPLACE | REPLACE | 是 | **Level 2**（人工决策） |
| CREATE | CREATE | —（同文件） | **Level 2**（人工决策） |
| ADD_SECTION | 任何 | 否 | Level 0 |
| ADD_SECTION | REPLACE | 是（after 的 section 被 replace） | Level 1 |
| DEPRECATE | APPEND | 是 | Level 0（先 DEPRECATE 再 APPEND） |
| DEPRECATE | REPLACE | 是 | Level 1（先 DEPRECATE 再 REPLACE，⚠️ 标注） |
| DEPRECATE | DEPRECATE | 是 | Level 0（合并废弃原因） |

---

## Known Issues 格式

每条 issue 必须包含 5 个字段（四要素 + 写入目标）：

```markdown
### ISSUE-N
- 问题：<一句话描述问题本质>
- 发现：<change_name>/<YYYY-MM-DD>
- 影响：<影响范围和严重程度>
- 建议：<修复方向或缓解措施>
- 写入目标：<file_path> @ "<section_title>"
```

`/archive-knowledge` 应用时：
1. 在写入目标文件中定位或创建「## ⚠️ 已知问题」章节
2. 追加格式化的 issue 条目：
   ```
   - [<change>/<日期>] <问题描述> — 影响：<范围> — 建议：<修复方向>
   ```

---

## work-stash.md 归档后修正章节格式

当变更经历 `/pipeline:amend`（归档后修正）时，重新归档的 work-stash.md 必须在原有内容之后追加以下章节：

```markdown
## 归档后修正
- 修正原因：<团队 CR 具体反馈内容>
- 原 commit：<首次归档时的 commit hash>
- 新 commit：<修正后的 commit hash>
- 修正日期：<YYYY-MM-DD>
- scope：<small/medium/large>
- 变更描述：<本次修正具体改了什么代码>
```

如果经历多次 amend，每次追加一个「## 归档后修正（第 N 次）」章节，按时间序排列。

### delta.md Meta 中的 Amendment 字段

Amendment 场景下，delta.md 的 `## Meta` 表格追加以下字段：

```
| amendment_commit | <修正后的 commit hash>（首次归档无此字段） |
| amendment_date | <修正日期> |
| amendment_reason | <一句话修正原因> |
| original_commit | <首次归档时的 commit hash> |
```

---

## 校验清单

Archiver 生成 delta.md 后必须逐项自检：

```
[ ] 文件以 "# Knowledge Delta: <name>" 开头
[ ] ## Meta 表格字段完整（7 个 key）
[ ] ## Consistency Audit Summary 表格包含 4 行（四维度），含 Intentional 列
[ ] ## Operations 部分至少有 1 个操作（或明确说明无知识更新的理由）
[ ] 每个 OP 的 H3 标题格式正确
[ ] 每个 ACTION 在 APPEND/REPLACE/CREATE/ADD_SECTION/DEPRECATE 之内
[ ] 每个 OP 的内容用 ```knowledge ``` 包裹
[ ] 每个需要 Section 定位的 OP（除 CREATE）有 context 注释（after/before）
[ ] context 中的 after/before 标题在目标文件中真实存在
[ ] APPEND/REPLACE/DEPRECATE 的 section 在目标文件中存在（已 Read 验证）
[ ] CREATE 的文件确实不存在（已 Glob 验证）
[ ] ADD_SECTION 的 after section 在目标文件中存在
[ ] DEPRECATE 的 knowledge block 包含 reason/alternative/since 三字段
[ ] ## Known Issues 每条包含 5 个字段
[ ] ## Lessons Learned 至少有 1 条
[ ] knowledge_baseline 与 CURRENT.md 的 current_version 一致
```
