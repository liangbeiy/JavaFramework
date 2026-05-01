---
name: "knowledge-init"
description: "为当前项目从零生成完整知识库。使用并行子 Agent 深度扫描代码，产出 modules（4 文件/模块）、cross-cutting、router 等全部文档。Invoke when user asks to 初始化知识库, init knowledge, 生成知识库, 重新生成知识库, or runs /knowledge-init."
---

# knowledge-init

为当前项目深度扫描代码，生成完整知识库。使用并行子 Agent 分别处理 modules / cross-cutting / router 三大维度。

## 触发条件

用户输入 `/knowledge-init` 或要求「初始化知识库」「生成知识库」「重新生成知识库」时激活。

---

## 前置条件

```
1. 项目已初始化 git 仓库
2. 项目根目录存在构建配置文件
3. knowledge/ 目录不存在或用户确认覆盖
```

---

## 关键结构约定

```
knowledge/
├── AGENTS.md                  ← 实体（项目根目录放符号链接 ln -sf knowledge/AGENTS.md）
├── CURRENT.md                 ← 版本元数据
├── current/
│   ├── _index.md              ← 文档索引 + 模块状态表
│   ├── architecture.md
│   ├── constitution.md
│   ├── glossary.md
│   ├── commit.md
│   ├── capabilities.yaml
│   ├── roles/                 ← 角色定义（由 fast-harness-init 复制，本 skill 不生成）
│   ├── style/
│   ├── modules/<Module>/      ← 每模块 4 个文件
│   │   ├── overview.md
│   │   ├── api.md
│   │   ├── business.md
│   │   └── data-model.md
│   ├── cross-cutting/
│   └── router/                ← 符号表 + 调用图 + 模块依赖
│       ├── symbol-table.tsv
│       ├── method-index.tsv
│       ├── call-graph.tsv
│       ├── module-deps.tsv
│       ├── ROUTER-META.md
│       └── QUERY-PROTOCOL.md
├── delta/
└── snapshots/
    └── _index.md
```

---

## 执行协议

**4 个步骤按序执行。**

---

### 步骤 1：项目探测

扫描项目根目录，识别：

```
1. 构建系统类型（Gradle / npm / Cargo / Maven / Go / CMake / Python / .NET）
2. 主要编程语言（top 3）+ 源码文件扩展名
3. 模块/包结构（从 settings.gradle / workspaces / Cargo workspace 等解析）
4. 入口点
5. 关键依赖（top 20）
6. 测试框架
7. 项目描述（从 README / 构建配置提取）
```

产出：内存中的 project-profile，供后续步骤使用。

---

### 步骤 2：创建目录 + 生成核心文档（主 Agent 直接执行）

#### 2.1 创建目录

```bash
mkdir -p knowledge/current/{style,cross-cutting,modules,router}
mkdir -p knowledge/delta
mkdir -p knowledge/snapshots
```

注意：`knowledge/current/roles/` 不在此创建（由 fast-harness-init 负责或手动复制）。如已存在则保留不动。

#### 2.2 `knowledge/CURRENT.md`

```
current_version: v1.0
generated_at: <今天日期>
status: draft
modules_count: <N>
hyperwiki_supplement: false
```

#### 2.3 `knowledge/current/constitution.md`

**[MUST-DO] 必须包含实质内容，从代码实际扫描得来。**

```
必须包含的章节：
- 项目概述（2-3 段，从 README + 代码推断）
- 技术栈表格（语言/构建/框架 + 版本号）
- 模块列表表格（模块名/路径/类型/职责）
- 架构约束（从依赖和代码推断至少 3 条）
- 依赖方向（明确分层）
```

无法确定的用「（待项目组确认）」标注，不留 `<!-- TODO -->`。

#### 2.4 `knowledge/current/architecture.md`

**[MUST-DO] 必须生成 Mermaid 依赖图。**

```
必须包含：
1. 高层架构描述（2-3 段）
2. 模块依赖关系 Mermaid 图（从构建配置实际声明生成）
3. 目录结构（深度 2-3 层）
4. 核心组件列表（职责 + 入口类）
5. 数据流描述（从依赖推断）
6. 进程/线程模型（如适用）
```

#### 2.5 `knowledge/current/_index.md`

```markdown
---
spec_version: 1.0.0
generated_by: knowledge-init
last_updated: <今天日期>
---

# 知识库文档索引

## 最新版本：<今天日期>_v1.0

## 项目信息
- 项目类型：<single_module / multi_module / monorepo>
- 特性：<关键技术特性>

## 版本历史
| 版本 | 日期 | 模块数 | 文档数 | 说明 |
|------|------|--------|--------|------|
| v1.0 | <今天日期> | <N> | <M> | 初次生成 |

## 当前版本模块状态
| 模块 | overview | api | data-model | business | 状态 |
|------|----------|-----|------------|----------|------|
| <每模块一行> |
```

初始状态标记为 `🔄`，子 Agent 完成后更新为 `✅`。

#### 2.6 `knowledge/current/glossary.md`

**[MUST-DO] 从代码实际提取术语，至少 10 条。**

```
扫描策略：
- 公开类/函数名中的驼峰/蛇形词组
- README / 注释中的领域词汇
- 出现频率 > 5 次的非通用编程术语
```

#### 2.7 `knowledge/current/commit.md`

提交规范。如项目有 `.commitlintrc` / CONTRIBUTING.md → 提取转化。否则使用通用模板。

#### 2.8 `knowledge/current/capabilities.yaml`

基于探测结果填入真实可执行命令。

#### 2.9 `knowledge/current/style/code-style.md`

**[MUST-DO] 基于项目实际配置生成。**

```
- 检查 .editorconfig / .prettierrc / rustfmt.toml / .eslintrc → 转化为文档
- 如无配置 → 扫描 5-10 个源码文件推断风格
```

#### 2.10 `knowledge/current/style/default-code-style.editorconfig`

已有 `.editorconfig` → 复制。否则基于推断生成。

#### 2.11 `knowledge/snapshots/_index.md`

```markdown
# Snapshots Index

| Version | Date | Status | Modules | Notes |
|---------|------|--------|---------|-------|
```

---

### 步骤 3：并行子 Agent 生成（modules / cross-cutting / router）

**[MUST-DO] 3 个子 Agent 必须并行发起。**

#### 3.1 子 Agent A：modules 文档

```
prompt：
─────────────────────────────────
你是 知识库模块文档生成 Agent。

任务：为以下模块各生成 4 个文档，写入 knowledge/current/modules/<Module>/。

项目根目录：<工作目录>
模块列表：
<从 constitution.md 提取的完整模块列表，含名称 + 路径>

对每个模块，创建目录并生成 4 个文件：

## overview.md
- 基本信息表格（模块类型/路径/namespace/业务域/源文件数）
- 模块定位（2-3 段描述，从代码扫描得来）
- 直接依赖（树形结构，从构建配置提取）
- 核心类列表（top 10，含一句话职责）

## api.md
- 对外暴露的 public 类/接口/函数签名
- 组件注册表（如 Android 四大组件、REST endpoints 等）
- 模块对外提供的主要能力

## business.md
- 业务流程描述
- 核心状态机/流程
- 边界条件和异常处理
- 与其他模块的交互流程

## data-model.md
- 数据实体/表结构/Schema
- 缓存策略（如有）
- 数据流向

规则：
[MUST-DO] 每个文件必须有实质内容，深度扫描模块 src/ 下源码
[MUST-DO] 源码少的模块（如薄壳/聚合模块）在内容中说明原因
[MUST-DO] 依赖关系必须从构建配置实际提取
─────────────────────────────────
```

**大项目策略**：模块 > 20 个时分批发起子 Agent，每批 ≤ 10 个模块。

#### 3.2 子 Agent B：cross-cutting 文档

```
prompt：
─────────────────────────────────
你是 知识库横切关注点文档生成 Agent。

任务：基于项目技术栈和代码，生成横切关注点文档。

项目根目录：<工作目录>
项目技术栈：<从 constitution.md 提取>
模块列表：<模块名 + 职责一句话>

自动判断需要哪些文档（按检测到的技术特征）：

| 技术特征 | 生成文档 |
|----------|---------|
| 多模块 | 整体架构与模块关系.md |
| 多进程/多线程 | 多进程架构.md 或 并发模型.md |
| HTTP/网络库 | 网络与API.md |
| 数据库/ORM | 数据持久化.md |
| CI/CD 配置 | 构建与发布.md |
| 认证/权限 | 认证与权限.md |
| IPC/消息机制 | IPC通信机制.md |
| UI 框架 | UI架构.md |

每个文档格式：
# <主题>
## 概述（1-2 段）
## 核心机制（具体描述，含代码引用和类名）
## 关键类/模块（表格列出）
## 约束与注意事项

规则：
[MUST-DO] 深度扫描相关代码产出实质内容
[MUST-DO] 至少生成 2 个文档
─────────────────────────────────
```

#### 3.3 子 Agent C：router 索引

```
prompt：
─────────────────────────────────
你是 路由索引生成 Agent。

任务：生成 AI 可查询的代码路由索引，写入 knowledge/current/router/。

项目根目录：<工作目录>
模块列表：<模块名 + 源码路径>

产出 6 个文件：

1. symbol-table.tsv
   ClassName\tModuleName\tFilePath\tType\tVisibility\tDescription
   扫描所有 public/internal 类/接口/枚举

2. method-index.tsv
   ClassName.methodName\tModule\tFilePath\tVisibility\tKind\tSignature
   扫描所有 public 方法（Kind: getter/setter/lifecycle/api/internal/callback）

3. call-graph.tsv
   Caller\tCallee\tType\tConfidence
   从方法体提取调用关系（Type: direct/interface/event/ipc）

4. module-deps.tsv
   FromModule\tToModule\tType\tEvidence
   从构建配置提取模块间依赖

5. ROUTER-META.md
   baseline_commit / generated_at / modules_scanned / 行数统计 / 覆盖率

6. QUERY-PROTOCOL.md
   查询协议说明（如何用 Grep 查询这些 TSV 文件）

规则：
[MUST-DO] TSV 文件必须用 \t 分隔，首行为表头
[MUST-DO] symbol-table 至少覆盖 80% 的 public 类
[RECOMMEND] 大项目（>30 模块）可限制每模块 top 50 方法
─────────────────────────────────
```

**[RECOMMEND] 模块 > 30 个时可询问用户是否跳过 router 生成。**

---

### 步骤 4：汇总 + 完成

```
1. 所有子 Agent 返回后：
   - 更新 _index.md 模块状态表（全部标为 ✅）
   - 统计总文档数，更新 CURRENT.md

2. 输出完成报告：

   ## 知识库生成完成

   ### 统计
   - 核心文档：9 个
   - 风格文档：2 个（+ roles 下已有的 delta-format / complex-score）
   - 模块文档：N 个模块 × 4 文件 = M 个
   - 横切关注点：K 个
   - 路由索引：symbol-table X 行 / method-index Y 行 / call-graph Z 行
   - 术语条目：J 条
   - 总文档数：<sum>

   ### 审查建议
   - constitution.md — 确认技术栈和约束准确
   - architecture.md — 确认依赖图完整
   - 抽查 2-3 个模块的 overview.md — 确认内容质量
```

---

## 硬性约束

```
[ASSERT-DISALLOW] 禁止修改项目源码文件
[ASSERT-DISALLOW] 禁止删除已有 knowledge/ 内容（除非用户确认覆盖）
[ASSERT-DISALLOW] 禁止编造不存在的模块或依赖关系
[ASSERT-DISALLOW] 每个模块必须生成 4 个文件（overview / api / business / data-model）
[MUST-DO]         所有文档必须有实质内容，无法确定的用「（待项目组确认）」
[MUST-DO]         模块依赖关系必须基于构建配置实际声明
[MUST-DO]         子 Agent A/B/C 必须并行发起
[MUST-DO]         glossary 术语必须从代码实际提取，至少 10 条
[MUST-DO]         capabilities.yaml 命令必须是真实可执行的
[MUST-DO]         CURRENT.md 在 knowledge/ 根下
[MUST-DO]         snapshots/_index.md 必须生成（空表头）
[RECOMMEND]       大项目 router 可选跳过
```

---

## 完成标准

```
--- 目录结构 ---
[ ] knowledge/current/ 含子目录：style / cross-cutting / modules / router
[ ] knowledge/delta/ 存在
[ ] knowledge/snapshots/_index.md 存在

--- 核心文档 ---
[ ] knowledge/CURRENT.md 存在且 version = v1.0
[ ] knowledge/current/constitution.md > 500 字符
[ ] knowledge/current/architecture.md 含 mermaid 代码块
[ ] knowledge/current/_index.md 含模块状态表
[ ] knowledge/current/glossary.md 含 > 5 条术语
[ ] knowledge/current/commit.md 含 commit message 格式
[ ] knowledge/current/capabilities.yaml 含 build-verify
[ ] knowledge/current/style/code-style.md 存在
[ ] knowledge/current/style/default-code-style.editorconfig 存在
[ ] knowledge/snapshots/_index.md 存在（含表头）

--- 模块文档 ---
[ ] 每个模块在 modules/<Module>/ 下有 4 个文件
[ ] overview.md 含基本信息表格 + 核心类列表
[ ] api.md 含 public 接口列表
[ ] business.md 含业务流程描述
[ ] data-model.md 含数据结构描述

--- 横切关注点 ---
[ ] cross-cutting/ 下至少 2 个文档
[ ] 每个文档含实质内容（非骨架标题）

--- 路由索引 ---
[ ] router/symbol-table.tsv 存在（或用户选择跳过）
[ ] router/method-index.tsv 存在（或跳过）
[ ] router/ROUTER-META.md 存在（或跳过）

--- 汇总 ---
[ ] _index.md 模块状态全部为 ✅
[ ] 完成报告已输出
```
