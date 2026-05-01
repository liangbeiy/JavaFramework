# Fast Harness Init

一键为任意项目配置完整的 AI 辅助开发流水线。

---

## 这是什么？

Harness 是一套 AI 辅助软件开发的工程化框架：

- **8 阶段流水线**：探索 → 设计 → CR₁ → 实现 → 验证 → CR₂ → 提交 → 归档
- **12 类自动门禁**：源码保护、自动推进、密钥扫描、风格校验等
- **7 个 AI 角色**：自动接力完成从需求到归档全流程
- **知识库体系**：深度扫描代码自动生成项目文档 + Delta 增量归档
- **跨会话记忆**：流水线状态持久化，断线后可恢复

---

## 使用方法（3 步）

### 第 1 步：复制到目标项目

```bash
cp -R /path/to/fast-harness-init  /你的项目/.opencode/skills/fast-harness-init
```

确保最终结构为：
```
你的项目/
└── .opencode/
    └── skills/
        └── fast-harness-init/
            ├── SKILL.md
            └── base/
                └── ...（50+ 文件）
```

### 第 2 步：重启 OpenCode

```bash
cd /你的项目
opencode
```

### 第 3 步：执行初始化

```
/fast-harness-init
```

然后按提示操作即可（确认探测结果 → 等待自动完成 → 审阅生成内容）。

---

## 自动化流程详解

```
/fast-harness-init
    │
    ├─ 阶段 1：项目探测
    │   扫描构建配置/语言/模块结构/依赖
    │   → 展示结果，你确认
    │
    ├─ 阶段 2：复制骨架
    │   流水线 skills + 门禁 hooks + 构建脚本 + 角色定义
    │   → npm install
    │
    ├─ 阶段 3：知识库生成（并行子 Agent）
    │   ├─ 主 Agent：核心文档（constitution/architecture/glossary/commit/...）
    │   ├─ 子 Agent A：modules 文档（每模块 4 个文件）
    │   ├─ 子 Agent B：cross-cutting 横切关注点
    │   ├─ 子 Agent C：router 路由索引（符号表/调用图）
    │   └─ 子 Agent D：AGENTS.md + 符号链接
    │
    ├─ 阶段 4：适配
    │   harness-hooks.ts + 构建脚本 + capabilities.yaml
    │
    ├─ 阶段 5：验证
    │   17 项完整性检查
    │
    └─ 阶段 6：完成
        输出报告 → 你审阅确认 → 重启即可使用
```

---

## 初始化后的项目结构

```
你的项目/
├── AGENTS.md                  ← 符号链接 → knowledge/AGENTS.md
├── .opencode/
│   ├── package.json
│   ├── node_modules/
│   ├── plugins/harness/
│   │   └── harness-hooks.ts   ← 12 类门禁（自动适配你的语言）
│   ├── tools/
│   │   └── pipeline-check.ts
│   ├── scripts/harness/        ← 构建/验证脚本（自动适配你的命令）
│   └── skills/                 ← 全部 Skill
├── knowledge/
│   ├── AGENTS.md               ← 实体文件
│   ├── CURRENT.md              ← 版本 v1.0
│   ├── current/
│   │   ├── _index.md           ← 文档索引
│   │   ├── constitution.md     ← 项目宪法
│   │   ├── architecture.md     ← 架构（含 Mermaid 依赖图）
│   │   ├── glossary.md         ← 术语表
│   │   ├── commit.md           ← 提交规范
│   │   ├── capabilities.yaml   ← 能力注册
│   │   ├── roles/              ← 7 角色定义
│   │   ├── style/              ← 代码风格 + 归档格式
│   │   ├── modules/            ← 每模块 4 文件
│   │   │   └── <Module>/
│   │   │       ├── overview.md
│   │   │       ├── api.md
│   │   │       ├── business.md
│   │   │       └── data-model.md
│   │   ├── cross-cutting/      ← 横切关注点
│   │   └── router/             ← 路由索引
│   │       ├── symbol-table.tsv
│   │       ├── method-index.tsv
│   │       ├── call-graph.tsv
│   │       ├── module-deps.tsv
│   │       ├── ROUTER-META.md
│   │       └── QUERY-PROTOCOL.md
│   ├── delta/                  ← 归档目录
│   └── snapshots/              ← 快照目录
└── .knowledge-work/            ← 运行时状态（gitignored）
    ├── pipeline-state.json
    ├── progress.md
    └── current-plan.md
```

---

## 初始化后可用的命令

| 命令 | 作用 |
|------|------|
| `/pipeline:new <name> [描述]` | 创建新变更，启动流水线 |
| `/pipeline:next` | 推进到下一阶段 |
| `/pipeline:status` | 查看流水线状态 |
| `/pipeline:goto <stage>` | 跳转到指定阶段 |
| `/pipeline:approve` | CR 通过（阶段 3/6） |
| `/pipeline:reject [原因]` | CR 打回（阶段 3/6） |
| `/pipeline:amend` | 归档后修正 |
| `/archive-knowledge` | 合并 Delta 到知识库 |

---

## 工作流示意

```
你: /pipeline:new fix-login-bug 修复登录崩溃

  阶段 1: Explorer 自动探索需求 → exploration.md
  阶段 2: Planner 自动设计方案 → design.md + spec.md + tasks.md
     ↓ 自动推进（无需人工）
  阶段 3: 等你审查方案
你: /pipeline:approve

  阶段 4: Builder 自动写代码
  阶段 5: Verifier 自动编译+测试+lint
     ↓ 自动推进
  阶段 6: 等你审查代码
你: /pipeline:approve

  阶段 7: Committer 自动提交（git commit）
  阶段 8: Archiver 自动归档到知识库
     ↓ 完成
```

---

## 支持的项目类型

| 构建系统 | 语言 |
|----------|------|
| Gradle | Java / Kotlin |
| npm / pnpm / yarn | TypeScript / JavaScript |
| Cargo | Rust |
| Maven | Java |
| Go modules | Go |
| CMake | C / C++ |
| pip / poetry | Python |
| .NET | C# |

---

## FAQ

**Q: 整个初始化需要多久？**

小项目（<10 模块）3-5 分钟，大项目（>30 模块）10-15 分钟。主要时间花在子 Agent 扫描代码生成模块文档。

**Q: 生成的知识库文档质量如何？**

AI 会深度扫描每个模块的源码、构建配置和依赖关系，产出的文档包含核心类列表、API 签名、业务流程和数据模型。准确率约 85-90%，建议审阅 constitution.md 和 architecture.md。

**Q: 不走流水线也能正常用吗？**

可以。不执行 `/pipeline:new` 时门禁不会拦截任何操作。流水线完全 opt-in。

**Q: 初始化完成后 `base/` 目录可以删吗？**

可以安全删除 `.opencode/skills/fast-harness-init/base/`。只在初始化时使用。

**Q: 门禁太严想调整？**

编辑 `.opencode/plugins/harness/harness-hooks.ts` 中的常量。比如想放宽源码保护，修改 `SOURCE_CODE_PATTERN`。

**Q: 知识库怎么更新？**

每次通过流水线完成变更后，Archiver 会自动产出 Delta 到 `knowledge/delta/`。积累几个 Delta 后执行 `/archive-knowledge` 合并到 `knowledge/current/`。

**Q: 多人协作怎么处理？**

`.knowledge-work/` 是本地状态（gitignored），每人独立。`knowledge/` 提交到 git 共享，通过 Delta 机制避免冲突。
