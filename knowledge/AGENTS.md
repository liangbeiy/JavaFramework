# AGENTS.md -- JavaFramework

## 规则分级（全局）

```Text
[ASSERT-DISALLOW] 严令禁止。违反必须立即停止并报告；
[DISALLOW] 禁止。与 ASSERT-DISALLOW 冲突时以后者为准；
[MUST-DO] 必须做。不遵守必须立即停止并报告；
[RECOMMEND] 推荐遵守；
```

## 启动协议

```Text
[ASSERT-DISALLOW] 无论用户第一条消息的内容是什么，都必须先完成启动协议全部步骤再响应用户请求。
```

```
1. 读取本文件（已自动加载）
2. 读取 knowledge/current/roles/orchestrator.md
3. 用 Bash(ls .knowledge-work/) 检查工作目录
   [ASSERT-DISALLOW] 禁止使用 Glob（目录在 .gitignore 中，Glob 永远返回空）
   - 不存在 → 创建目录 + progress.md + current-plan.md
   - 已存在 → 不覆写已有文件
4. 读取 pipeline-state.json + progress.md + current-plan.md → 恢复状态
5. 向用户报告工作区状态，然后处理请求
```

若 `progress.md` 有进行中的任务，**优先继续未完成的任务**。

## 工作语言

```Text
[MUST-DO] 工作语言：简体中文（代码/编程术语除外）
[MUST-DO] 输出英语时附翻译，遵循美式英语（en-US）规范
[DISALLOWED] 严禁成段输出其他语言
```

## Project Overview

一个 Java 脚手架项目，提供事件总线、协程调度、IO 操作、HTTP 客户端等基础框架能力。

## Architecture

JavaFramework 是一个模块化 Java 脚手架框架，提供事件驱动、协程调度、IO 封装等基础能力。

核心组件包括 EventBus（进程内/跨进程事件总线）、Coroutine（协程调度框架）、IO（KV存储和文件操作）和 Context（框架全局上下文）。

系统采用分层模块化架构，framework 作为基础模块，handler/http/server 作为上层业务模块依赖 framework。

数据流：Event Source → EventBus.subscribe() → Handler.invoke() → Coroutine.dispatch() → IO/Network/Compute

关键设计模式：Observer Pattern（EventBus + @Subscribe）、Provider Pattern（FileProvider/KVProvider）、Strategy Pattern（DispatcherQueue）、Singleton（FrameworkContext）。

## Source Code Modification Guard

```Text
[硬拦截] 由 .opencode/plugins/harness/harness-hooks.ts 门禁 11 执行代码级拦截。
  源码 Edit/Write 在 currentStage < 4 时会被 plugin 直接 throw Error 阻断。

[ASSERT-DISALLOW] Orchestrator 层面仍须遵守意图引导：
  - 用户意图修正已归档代码 → 必须 /pipeline:amend
  - 无活跃变更 → 必须 /pipeline:new
```

不受约束：`.knowledge-work/`、`knowledge/`、`AGENTS.md`、`.opencode/`、`.claude/`

## Stage Work Execution Guard

```Text
[ASSERT-DISALLOW] Orchestrator 禁止直接执行阶段工作。一律通过 Task tool 发起子 Agent。

  阶段 → 角色映射：
    1→Explorer | 2→Planner | 4→Builder | 5→Verifier | 7→Committer | 8→Archiver
```

## Auto-Advance Guard

```Text
[硬信号] 由 .opencode/plugins/harness/harness-hooks.ts 门禁 12 + .opencode/tools/pipeline-check.ts 执行。
  子 Agent 返回后 plugin 自动输出 NEXT_ACTION 指令。

[ASSERT-DISALLOW] 非门禁阶段（1/2/4/5/7/8）切换时：
  - 禁止向用户确认。以下输出模式为违规：
    "需要继续吗？" / "是否推进？" / "要我开始吗？" / "需要我继续推进吗？"
  - 必须在同一条回复中完成：更新状态 → 发起下一个子 Agent
  - 人工门禁阶段（3/6）：向用户请求审查结论
```

## Architecture Constraints

1. 框架模块（framework）是基础模块，不被其他业务模块反向依赖
2. handler 模块依赖 framework 提供的事件总线和处理能力
3. http 和 server 模块可独立使用，也可组合使用
4. 所有跨进程通信必须通过 eventbus.cross 包提供的协议
5. 协程调度器（coroutine）禁止在 IO 线程执行阻塞操作
6. KV 存储（io.kv）和文件操作（io.file）应提供统一的 Provider 接口
7. 注解处理器（annotation.processor）在编译期生成代码，运行时无反射开销
8. 版本依赖必须锁定（Gradle dependency locking）

## Knowledge Base

- 入口：`knowledge/current/_index.md`（模块状态总览）
- 架构与宪法：`current/architecture.md` | `current/constitution.md`
- 术语：`current/glossary.md` | 提交规范：`current/commit.md`
- 模块文档：`current/modules/{ModuleName}/overview.md`
- 横切关注点：`current/cross-cutting/`
- 代码风格：`current/style/code-style.md`
- 角色定义：`current/roles/`
- 能力声明：`current/capabilities.yaml`
- 路由索引：`current/router/`
- 待合并 Delta：`knowledge/delta/` | 历史快照：`knowledge/snapshots/`

## Roles

| 角色 | 文件 | 一句话职责 |
|------|------|-----------|
| Orchestrator | `roles/orchestrator.md` | 流程调度、用户交互、跨会话状态维护 |
| Explorer | `roles/explorer.md` | 将模糊需求转化为结构化的 exploration.md |
| Planner | `roles/planner.md` | 将需求探索转化为 design.md + spec.md + tasks.md + unit-task.md |
| Builder | `roles/builder.md` | 将已审批方案转化为代码（唯一允许改源码的角色） |
| Verifier | `roles/verifier.md` | 执行构建命令并报告结果（只读角色） |
| Committer | `roles/committer.md` | 风格终审 + 按 commit.md 规范提交（不 push） |
| Archiver | `roles/archiver.md` | 一致性审计 → 生成 Delta 归档包 → 清理工作区 |
| Amender | `roles/amender.md` | 归档后修正协调 → 恢复工作区 → 按 scope 重新纳入流水线 |
