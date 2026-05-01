---
name: "fast-harness-init"
description: "一键为当前项目配置完整 Harness 流水线（8 阶段 + 12 门禁 + 知识库 + 角色体系）。自动探测项目、复制骨架、并行子 Agent 深度生成知识库、适配门禁。Invoke when user asks to 初始化 harness, 配置流水线, init harness, setup pipeline, or runs /fast-harness-init."
---

# fast-harness-init

一键为当前项目配置完整 Harness 体系。自动化完成全部配置，用户仅需在关键节点审查确认。

## 触发条件

用户输入 `/fast-harness-init` 时激活。在当前工作目录执行。

---

## 前置条件

```
1. 当前项目已初始化 git 仓库
2. 项目根目录存在构建配置文件
3. .opencode/skills/fast-harness-init/base/ 目录存在
```

---

## 关键结构约定

```
knowledge/                     ← 知识库根目录
├── AGENTS.md                  ← AGENTS 实体文件（项目根目录放符号链接）
├── CURRENT.md                 ← 版本元数据
├── config.json                ← 知识库配置（可选）
├── current/                   ← 当前版本知识
│   ├── _index.md              ← 文档索引
│   ├── architecture.md
│   ├── constitution.md
│   ├── glossary.md
│   ├── commit.md
│   ├── capabilities.yaml
│   ├── roles/                 ← 角色定义
│   ├── style/                 ← 代码风格
│   ├── modules/<Module>/      ← 每模块 4 个文件
│   │   ├── overview.md
│   │   ├── api.md
│   │   ├── business.md
│   │   └── data-model.md
│   ├── cross-cutting/         ← 横切关注点
│   └── router/                ← 路由索引（符号表+调用图）
├── delta/                     ← 待合并归档
└── snapshots/                 ← 版本快照

项目根/AGENTS.md → symlink → knowledge/AGENTS.md
```

---

## 执行协议

**6 个阶段按序执行。仅阶段 1 和阶段 6 需要用户交互。**

---

### 阶段 1：项目深度探测 + 用户确认

#### 1.1 构建系统识别

| 配置文件 | 构建系统 | 编译命令 | 测试命令 | lint 命令 |
|----------|---------|---------|---------|-----------|
| `build.gradle` / `settings.gradle` | Gradle | `./gradlew assembleDebug` | `./gradlew test` | `./gradlew lint` |
| `package.json` | npm | `npm run build` | `npm test` | `npm run lint` |
| `Cargo.toml` | Cargo | `cargo build` | `cargo test` | `cargo clippy` |
| `pom.xml` | Maven | `mvn compile` | `mvn test` | `mvn checkstyle:check` |
| `go.mod` | Go | `go build ./...` | `go test ./...` | `golangci-lint run` |
| `CMakeLists.txt` | CMake | `cmake --build build` | `ctest --test-dir build` | `clang-tidy` |
| `pyproject.toml` | Python | `python -m build` | `pytest` | `ruff check .` |
| `*.sln` | .NET | `dotnet build` | `dotnet test` | `dotnet format --verify-no-changes` |

#### 1.2 语言与模块扫描

```
- 统计各语言文件数（排除 build/node_modules/target/.git）
- 提取源码扩展名列表
- 解析模块结构（settings.gradle include / workspaces / workspace members 等）
- 识别入口点和关键依赖
- 读取 README.md 提取项目描述
```

#### 1.3 展示并确认

```
## 项目探测结果
| 维度 | 检测值 |
|------|--------|
| 项目名称 | <name> |
| 构建系统 | <type> |
| 主要语言 | <lang> |
| 源码扩展名 | <exts> |
| 模块数 | <N> |
| 编译命令 | <cmd> |
| 测试命令 | <cmd> |
| Lint 命令 | <cmd> |
| 测试框架 | <framework> |

确认？如需修正请告知。
```

等待用户确认后继续。

---

### 阶段 2：复制 base 骨架 + 安装依赖

```
1. 从 .opencode/skills/fast-harness-init/base/ 复制到项目根：
   - .opencode/skills/*           → 覆盖
   - .opencode/tools/*            → 覆盖
   - .opencode/scripts/harness/*  → 覆盖
   - .opencode/package.json       → 覆盖
   - knowledge/current/roles/*    → 全量
   - knowledge/current/style/delta-format.md   → 覆盖
   - knowledge/current/style/complex-score.md  → 覆盖
   - knowledge/delta/             → 创建空目录
   - knowledge/snapshots/         → 创建空目录
   - .knowledge-work/*            → 初始文件

2. 将 base/.gitignore.append 追加到 .gitignore（去重）

3. cd .opencode && npm install

4. 报告进度
```

---

### 阶段 3：知识库生成（并行子 Agent）

本阶段使用 **4 个子 Agent 并行执行**，各自负责知识库的一个维度。

#### 3.0 前置：生成核心文档（主 Agent 直接执行）

以下文件由主 Agent 直接生成（它们是子 Agent 的输入前提）：

| 文件 | 内容 |
|------|------|
| `knowledge/CURRENT.md` | 版本元数据（v1.0） |
| `knowledge/current/constitution.md` | 项目宪法（技术栈/模块列表/架构约束/依赖方向） |
| `knowledge/current/architecture.md` | 架构总览（含 Mermaid 依赖图） |
| `knowledge/current/_index.md` | 文档索引 + 模块状态表 |
| `knowledge/current/glossary.md` | 术语表（从代码提取） |
| `knowledge/current/commit.md` | 提交规范 |
| `knowledge/current/capabilities.yaml` | 能力注册表 |
| `knowledge/current/style/code-style.md` | 代码风格规范 |
| `knowledge/current/style/default-code-style.editorconfig` | EditorConfig |
| `knowledge/snapshots/_index.md` | 快照索引（空） |

**[MUST-DO] 所有文档必须包含实质内容。无法确定的用「（待项目组确认）」标注。**

#### 3.1 子 Agent A：modules 文档生成

```
prompt 模板：
─────────────────────────────────
你是 知识库模块文档生成 Agent。

任务：为以下模块各生成 4 个文档（overview.md / api.md / business.md / data-model.md）。

项目根目录：<工作目录>
模块列表：<从 constitution.md 提取的模块列表 + 路径>

对每个模块：
1. 扫描 src/ 下全部源码文件
2. 读取构建配置确认依赖关系
3. 生成 4 个文档，写入 knowledge/current/modules/<Module>/

## overview.md 格式
- 基本信息表格（模块类型/gradle路径/namespace/业务域/源文件数）
- 模块定位（2-3 段描述）
- 直接依赖（树形结构）
- 核心类列表（top 10，含一句话职责）

## api.md 格式
- 对外暴露的 public 类/接口/函数签名
- 组件注册表（Android 四大组件，如适用）
- REST/RPC/Bridge API（如适用）

## business.md 格式
- 业务流程描述
- 核心状态机/流程图
- 边界条件和异常处理

## data-model.md 格式
- 数据实体/表结构
- 缓存策略
- 数据流向

每个文档必须有实质内容。源码文件少的模块（如薄壳模块）可在内容中说明。
─────────────────────────────────
```

**大项目策略**：模块 > 20 个时分批，每批 ≤ 10 个模块发一个子 Agent。

#### 3.2 子 Agent B：cross-cutting 文档生成

```
prompt 模板：
─────────────────────────────────
你是 知识库横切关注点文档生成 Agent。

任务：基于项目代码和依赖分析，生成横切关注点文档。

项目根目录：<工作目录>
项目技术栈：<从 constitution.md 提取>
模块列表：<模块名 + 职责一句话>

生成规则：
- 根据检测到的技术特征决定需要哪些文档
- 每个文档深度扫描相关代码产出实质内容
- 写入 knowledge/current/cross-cutting/

| 技术特征 | 生成文档 |
|----------|---------|
| 多模块 | 整体架构与模块关系.md |
| 多进程/多线程 | 多进程架构.md 或 并发模型.md |
| HTTP/网络库 | 网络与API.md |
| 数据库/ORM | 数据持久化.md |
| CI/CD 配置 | 构建与发布.md |
| 认证/权限库 | 认证与权限.md |
| IPC/消息 | IPC通信机制.md |
| UI 框架 | UI 架构.md |

每个文档格式：
## 概述（1-2 段）
## 核心机制（具体描述，含代码引用）
## 关键类/模块（表格列出）
## 约束与注意事项
─────────────────────────────────
```

#### 3.3 子 Agent C：router 索引生成

```
prompt 模板：
─────────────────────────────────
你是 路由索引生成 Agent。

任务：为项目生成 AI 可查询的代码路由索引。

项目根目录：<工作目录>
模块列表：<模块名 + 源码路径>

产出文件写入 knowledge/current/router/：

1. symbol-table.tsv
   格式：ClassName\tModuleName\tFilePath\tType\tVisibility\tDescription
   扫描所有 public/internal 类/接口/枚举

2. method-index.tsv
   格式：ClassName.methodName\tModule\tFilePath\tVisibility\tKind\tSignature
   扫描所有 public 方法

3. call-graph.tsv
   格式：Caller\tCallee\tType\tConfidence
   从方法体中提取调用关系

4. module-deps.tsv
   格式：FromModule\tToModule\tType\tEvidence
   从构建配置提取模块间依赖

5. ROUTER-META.md
   记录生成时间、扫描范围、行数统计

6. QUERY-PROTOCOL.md
   查询协议说明文档
─────────────────────────────────
```

**[RECOMMEND] router 生成耗时较长，如模块 > 30 个，可询问用户是否跳过此步。**

#### 3.4 子 Agent D：AGENTS.md 生成

```
prompt 模板：
─────────────────────────────────
你是 AGENTS.md 生成 Agent。

任务：基于已生成的知识库文档，生成 knowledge/AGENTS.md。

读取以下文件获取信息：
- knowledge/current/constitution.md → 项目概述、技术栈、架构约束
- knowledge/current/architecture.md → 架构描述

使用模板 .opencode/skills/fast-harness-init/base/AGENTS.md.tmpl 替换占位符：
- {{PROJECT_NAME}} → constitution.md 中的项目名
- {{PROJECT_OVERVIEW}} → constitution.md 的项目概述章节
- {{ARCHITECTURE}} → architecture.md 的核心描述（精简 10-20 行）
- {{ARCHITECTURE_CONSTRAINTS}} → constitution.md 的架构约束（编号列表）

写入 knowledge/AGENTS.md。

完成后在项目根目录创建符号链接：
  ln -sf knowledge/AGENTS.md AGENTS.md

[ASSERT-DISALLOW] 产出不能包含 {{ 或 }} 字符串。
─────────────────────────────────
```

#### 3.5 并行策略

```
[MUST-DO] 子 Agent A/B/C/D 必须并行发起（同一条消息多个 Task tool call）。
[MUST-DO] 子 Agent A 依赖 constitution.md（阶段 3.0 已生成），可立即启动。
[MUST-DO] 子 Agent D 依赖 constitution.md + architecture.md，可立即启动。
[MUST-DO] 所有子 Agent 返回后汇总结果，更新 _index.md 的模块状态。
```

---

### 阶段 4：适配项目特有文件

#### 4.1 生成 `harness-hooks.ts`

从 `base/.opencode/plugins/harness/harness-hooks.ts.tmpl` 渲染：

| 需填充 | 数据来源 |
|--------|---------|
| `SOURCE_CODE_PATTERN` | 阶段 1 的源码扩展名正则 |
| `SECRET_SCAN_FILE_PATTERN` | 同上 |
| `LOCKED_DEPENDENCIES` | constitution.md 架构约束中的版本锁定 |
| `CRITICAL_FILES` | 阶段 1 的入口文件 |
| `BUILD_CONFIG_FILES` | 阶段 1 的构建配置文件 |

写入 `.opencode/plugins/harness/harness-hooks.ts`。

**[ASSERT-DISALLOW] 产出必须是合法 TypeScript，无占位符。**

#### 4.2 适配构建脚本

| 脚本 | 适配内容 |
|------|---------|
| `verify-build.sh` | 替换编译命令 |
| `verify-lint.sh` | 替换 lint 命令 |
| `verify-all.sh` | 注释不适用的步骤 |
| `verify-code-style.sh` | 按语言调整检查逻辑 |
| `verify-secrets.sh` | 调整文件扩展名 |

#### 4.3 适配 `capabilities.yaml`

确保所有 command 字段为真实可执行命令。

---

### 阶段 5：完整性验证

自动验证以下清单，任一失败则报告并尝试修复：

```
[ ] .opencode/plugins/harness/harness-hooks.ts 存在且无 {{}} 占位符
[ ] .opencode/tools/pipeline-check.ts 存在
[ ] .opencode/node_modules/@opencode-ai/plugin 存在
[ ] .opencode/skills/ 包含 pipeline-new 等 7 个 pipeline skill
[ ] knowledge/AGENTS.md 存在且无 {{}} 占位符
[ ] 项目根/AGENTS.md 是指向 knowledge/AGENTS.md 的符号链接
[ ] knowledge/CURRENT.md 存在
[ ] knowledge/current/_index.md 存在
[ ] knowledge/current/constitution.md > 500 字符
[ ] knowledge/current/architecture.md 含 mermaid 代码块
[ ] knowledge/current/glossary.md 含 > 5 条术语
[ ] knowledge/current/capabilities.yaml 含 build-verify
[ ] knowledge/current/roles/ 含 11 个 .md 文件
[ ] knowledge/current/modules/ 下每个模块含 4 个文件
[ ] knowledge/current/router/ 含 symbol-table.tsv（或用户选择跳过）
[ ] .knowledge-work/pipeline-state.json 存在
[ ] .gitignore 包含 .knowledge-work/
```

---

### 阶段 6：用户审查 + 完成

#### 6.1 完成报告

```
## Harness 初始化完成

### 配置统计
| 类别 | 数量 |
|------|------|
| 流水线阶段 | 8 |
| 自动门禁 | 12 |
| AI 角色 | 7 |
| 模块文档 | N 个模块 × 4 文件 |
| 横切关注点 | M 个 |
| 路由索引 | K 条符号 |
| 术语条目 | J 条 |

### 可用命令
| 命令 | 作用 |
|------|------|
| /pipeline:new <name> | 创建新变更 |
| /pipeline:next | 推进阶段 |
| /pipeline:status | 查看状态 |
| /pipeline:approve | CR 通过 |
| /pipeline:reject | CR 打回 |
| /pipeline:amend | 归档修正 |
| /archive-knowledge | 合并 Delta |
```

#### 6.2 请求审查

```
以下文件已自动生成完整内容，请审阅核心文档确认准确性：

1. knowledge/AGENTS.md — 项目概述和架构约束
2. knowledge/current/constitution.md — 技术栈和模块列表
3. knowledge/current/architecture.md — 依赖图

如有错误直接告知我修正。确认后请重启 OpenCode，然后尝试：
  /pipeline:new test-init 验证流水线是否正常
```

---

## 硬性约束

```
[ASSERT-DISALLOW] 禁止修改项目源码文件
[ASSERT-DISALLOW] harness-hooks.ts 必须是合法可执行 TypeScript
[ASSERT-DISALLOW] AGENTS.md 不能包含 {{}} 占位符
[ASSERT-DISALLOW] AGENTS.md 实体必须在 knowledge/ 下，根目录为符号链接
[ASSERT-DISALLOW] 知识库文档必须有实质内容，禁止留 <!-- TODO --> 占位符
[MUST-DO]         每个模块必须生成 4 个文件：overview.md / api.md / business.md / data-model.md
[MUST-DO]         模块文档内容必须来自实际代码扫描
[MUST-DO]         glossary 术语必须从代码中实际提取
[MUST-DO]         capabilities.yaml 的命令必须是真实可执行命令
[MUST-DO]         子 Agent A/B/C/D 必须并行发起
[MUST-DO]         阶段 1 必须获得用户确认
[MUST-DO]         阶段 5 验证全部通过
[MUST-DO]         阶段 6 输出报告并请求审查
[RECOMMEND]       router 索引对大项目可选跳过
```

---

## 完成标准

```
[ ] 阶段 1：探测结果已展示，用户已确认
[ ] 阶段 2：base 文件已复制，npm install 成功
[ ] 阶段 3.0：核心文档已生成
[ ] 阶段 3.1-3.4：4 个子 Agent 全部完成
[ ] 阶段 4：hooks + scripts + capabilities 已适配
[ ] 阶段 5：验证清单全部通过
[ ] 阶段 6：完成报告已输出，用户已知晓下一步
```
