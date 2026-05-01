---
name: "tech-plan-gen"
description: "独立技术方案生成器（不走 8 阶段流水线）。快速产出技术方案。Invoke when user asks to generate a tech plan outside the pipeline, 生成技术方案。"
---

# tech-plan-gen

独立技术方案生成器。**不走 8 阶段流水线时**的快速方案产出工具。

## 与 openspec-propose 的关系

```
openspec-propose（阶段 2 Planner）：
  - 流水线内使用，输入必须来自 Explorer 的 exploration.md
  - 产出经人工 CR 后进入 Builder
  - 适用场景：正式需求开发、架构变更

tech-plan-gen（独立 Skill）：
  - 流水线外使用，直接接收用户需求描述或 PRD URL
  - 产出作为独立技术方案文档，不进入流水线
  - 适用场景：快速技术评估、方案预研、技术讨论
```

**核心区别**：`openspec-propose` 是流水线中的一环（有门禁约束），`tech-plan-gen` 是独立工具（快速灵活）。两者产出格式一致。

## 触发条件

用户在流水线外要求生成技术方案时使用。典型指令：
- "根据这个需求生成技术方案"
- "帮我做技术方案"
- "快速评估一下这个需求的实现方案"

**注意**：如果用户明确要走 8 阶段流水线，应使用 `openspec-explore` → `openspec-propose` 链路，不用此 Skill。

---

## 输入

- **需求描述**：用户提供的需求文本，或飞书 PRD 文档 URL（使用 feishu-mcp fetch-doc 获取）
- **目标模块**：涉及的模块名称（可选，自动推断）

---

## 执行步骤

### 1. 需求解析

```
1. 若输入为飞书 PRD URL，先使用 feishu-mcp fetch-doc 获取全文
2. 提取核心功能点（做什么）
3. 识别非功能需求（性能/兼容性/安全）
4. 标注边界（不做什么）
```

### 2. 知识库分析

```
1. 读取 knowledge/current/architecture.md — 模块依赖与进程模型
2. 读取目标模块 modules/<Module>/overview.md + api.md — 现有能力
3. 读取相关 cross-cutting/ 文档 — 横切约束
4. 读取 constitution.md — 技术栈约束
```

### 3. 代码路由定位

```
1. 通过 router/symbol-table.tsv 定位相关类
2. 通过 router/call-graph.tsv 分析调用链
3. Read 源码确认实际实现
```

### 4. 影响分析

```
1. 识别需要修改的文件列表（精确到文件路径）
2. 评估每个修改点的风险等级
3. 检查架构约束（依赖方向/版本锁定/进程限制等）
4. 列出需要同步更新的文档（如 Bridge 文档）
```

### 5. 方案产出

产出 3 个文件（格式与 openspec-propose 一致）：

**design.md**：
- Context / Goals / Non-Goals / Decisions（含 rationale + rejected alternatives）/ Risks

**spec.md**（标准 8 章节）：
- 第 1 章：需求概述（含输入来源标注）
- 第 2 章：功能规格（GIVEN/WHEN/THEN 行为规约，按 capability 拆分）
- 第 3 章：UI/交互规格（页面清单、交互流程，无则标注「不适用」）
- 第 4 章：接口规格（接口签名、请求/响应、错误码，无则标注「不适用」）
- 第 5 章：实现计划（按 commit 粒度拆解文件清单）
- 第 6 章：验收标准（可验证 AC 条目）
- 第 7 章：测试要点（P0/P1/P2 场景）
- 第 8 章：风险与依赖

**tasks.md**：
- 按 Investigation → Core → Verification 分阶段
- 每 task 标注操作类型 + 精确文件路径 + 规模预估 [S/M/L]
- 每 task 对应可独立提交的 commit 粒度
- 单批 ≤ 3 个 task

### 6. 方案验证

自检清单：
- [ ] 每个 Decision 有 rationale 和 rejected alternative
- [ ] spec 第 2 章无模糊描述（如"应该正常工作"）
- [ ] spec 包含全部 8 个章节标题
- [ ] task 总数合理（bugFix 2-4, 需求 5-7）
- [ ] task 文件清单与 spec 第 5 章一致
- [ ] 不违反任何架构约束
- [ ] 涉及 Bridge API 修改时有独立文档更新 task

---

## 量化目标

| 指标 | 目标 |
|------|------|
| 技术方案准确性 | ≥ 85% |
| 人工 CR 一次通过率 | ≥ 80% |
