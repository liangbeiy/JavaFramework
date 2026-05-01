---
name: "openspec-propose"
description: "阶段 2 设计方案。将需求探索转化为 design.md + spec.md + tasks.md + unit-task.md。Invoke when user wants to propose a change or generate a complete proposal."
---

# openspec-propose

阶段 2 — 设计方案。将 Explorer 的需求探索转化为一套完整的变更方案制品（design.md + spec.md + tasks.md + unit-task.md），一步到位准备好实现所需的全部上下文。

当准备好实现时，由 Orchestrator 发起阶段 4 (apply-change)。

## 触发条件

Orchestrator 发起阶段 2 子 Agent 时自动加载。

---

## 执行协议

### 步骤 1：初始化上下文

```
1. 读取 AGENTS.md — 获取 Architecture Constraints
2. 读取 knowledge/current/roles/planner.md — 获取完整角色规范（本 Skill 的权威来源）
3. 确认变更名称 <change>
4. 读取 .knowledge-work/<change>/exploration.md — 唯一需求输入来源
5. 识别输入模式（口头/PRD）：检查 exploration.md 是否包含「PRD 信息提取」章节
   - PRD 模式：重点参考「PRD 信息提取」中已结构化的需求信息
   - 口头模式：按常规流程处理
```

### 步骤 2：知识库深度阅读

```
[必读] 相关模块 modules/<Module>/overview.md + api.md + business.md + data-model.md
[必读] knowledge/current/style/code-style.md — 使方案中代码示例遵循项目风格
[按需] knowledge/current/cross-cutting/ 涉及的横切文档
[按需] knowledge/current/architecture.md — 模块依赖与进程模型
```

### 步骤 3：代码路由定位

涉及代码定位时 **必须先查路由索引**：

```
1. symbol-table.tsv — 定位类
2. method-index.tsv — 定位方法
3. call-graph.tsv — 调用链分析
4. 定位后 Read 源码求证，知识库与源码矛盾时以源码为准
```

### 步骤 4：历史快照回溯

涉及重构或行为变更时（全新功能可跳过并说明理由）：

```
1. 读取 knowledge/snapshots/_index.md
2. 对比快照与 current/ 的差异，提取：
   - 历史架构决策（为什么当初这样设计）
   - 演进轨迹（经过哪些版本迭代）
   - 已知陷阱（已知问题章节）
3. 写入 design.md Context 章节
```

### 步骤 5：按依赖序创建制品

使用 **TodoWrite** 追踪创建进度。四个制品按依赖序生成：

```
依赖关系：exploration.md → design.md → spec.md → tasks.md → unit-task.md
                                         ↑ spec 第 5 章和 tasks.md 对齐
                                                     ↑ spec 第 7 章和 unit-task.md 对齐
```

创建过程中如遇到**上下文不足以做出合理决策**的情况，应暂停并向 Orchestrator 报告需要用户澄清的问题，而非自行猜测。优先保持推进节奏，但关键决策不含糊。

#### 5a. 撰写 design.md

写入 `.knowledge-work/<change>/design.md`：

```markdown
# Design — <change>

## Context
（问题背景、动机；涉及现有功能时包含现状分析；引用快照信息）

## Goals
（明确做什么）

## Non-Goals
（明确不做什么）

## Decisions
（每个决策附 rationale + rejected alternatives）

## Risks / Trade-offs
（每个风险附缓解策略）

## 对外影响评估（如涉及接口变更）
（接口兼容性、版本策略）
```

#### 5b. 撰写 spec.md（标准 8 章节规格文件）

写入 `.knowledge-work/<change>/spec.md`，必须包含全部 8 个章节（不适用时标注理由，禁止省略）：

```markdown
# Spec — <change>

## 第 1 章：需求概述
- 一句话描述：
- 背景：
- 目标：
- 非目标：
- 输入来源：（口头需求 / PRD URL / Jira Issue）

## 第 2 章：功能规格

### Capability 1: <名称>

#### ADDED Requirements（新增行为）
GIVEN <前置条件>
WHEN <触发动作>
THEN <预期结果>

#### MODIFIED Requirements（变更行为，bugFix 必须包含回归场景）
GIVEN <前置条件>
WHEN <触发动作>
THEN <预期结果>（标注修改前后差异）

## 第 3 章：UI/交互规格
- 页面清单：（涉及的 Activity/Fragment，或标注「不适用」）
- 交互流程：
- 设计稿链接：（Figma URL，或标注「无设计稿」）

## 第 4 章：接口规格
- 新增/修改的接口签名
- 请求/响应结构
- 错误码定义
（无接口变更时标注「不适用：本次变更不涉及接口」）

## 第 5 章：实现计划
（按 commit 粒度拆解，与 tasks.md 保持一致）
- Commit 1: <描述>
  - 修改：<文件路径>
  - 新增：<文件路径>

## 第 6 章：验收标准
- AC-1: GIVEN ... WHEN ... THEN ...
- AC-2: 输入 ... → 预期输出 ...

## 第 7 章：测试要点
按被测 capability 分组，每个场景使用唯一编号，标注优先级和验证方式。
本章只定义测试场景和优先级，不包含任何实现细节（测试类名、方法名、Mock 策略、
断言策略、文件路径、测试类型等全部移至 unit-task.md）。

### Capability 1: <名称>
- T-01 [P0] <测试场景描述>（单元测试）
- T-02 [P0] <测试场景描述>（单元测试）
- T-03 [P1] <测试场景描述>（手动验证）

### Capability 2: <名称>
- T-04 [P1] <测试场景描述>（单元测试）
- T-05 [P2] <测试场景描述>（手动验证）

## 第 8 章：风险与依赖
- 风险：（继承 design.md + 实现层面技术风险）
- 外部依赖：（第三方库 / 后端接口 / 运行时环境）
```

第 7 章约束：
- 每个场景使用全局唯一编号（T-01, T-02, ...），供 unit-task.md 追溯引用
- 每个场景标注优先级（P0 必测 / P1 重要 / P2 一般）
- 每个场景标注验证方式：「单元测试」或「手动验证」
- 至少有 1 个 P0/P1 场景标注为「单元测试」
- 禁止包含测试实现细节（类名、方法名、Mock 策略、断言策略、文件路径、测试类型）
- 推荐按被测 capability 分组组织

#### 5c. 撰写 tasks.md

写入 `.knowledge-work/<change>/tasks.md`，每个 task 对应一个可独立提交的 commit 粒度。

**tasks.md 只包含业务代码任务，禁止包含 Unit Testing 阶段或任何测试编写 task。**

```markdown
# Tasks — <change>

## Phase 1: Investigation
- [ ] 1.1 <task 描述>（修改 <文件路径>）[S]
  - 文件：`<精确文件路径>`

## Phase 2: Core Implementation
- [ ] 2.1 <task 描述>（新增 <文件路径>）[M]
  - 文件：`<精确文件路径>`
- [ ] 2.2 <task 描述>（修改 <文件路径>）[L]
  - 文件：`<精确文件路径1>`, `<精确文件路径2>`

## Phase 3: Verification
- [ ] 3.1 <task 描述> [S]
```

约束：
- 只包含 3 个阶段：Investigation → Core Implementation → Verification
- 禁止包含 Unit Testing 阶段或任何测试编写 task（测试任务全部在 unit-task.md 中定义）
- 每个 task 标注「新增」或「修改」+ 精确文件路径
- 每个 task 附带规模预估：[S] <30 行 / [M] 30-100 行 / [L] >100 行
- 每个 task 完成后代码必须处于可编译状态（commit 粒度）
- 文件清单须与 spec.md 第 5 章「实现计划」保持一致
- 单次 Builder 可处理 ≤ 3 个 task，超出需分批
- 涉及 Bridge API 修改时包含独立的文档更新 task
- 总 task 数推荐 3-7 个

#### 5d. 撰写 unit-task.md

在 design.md + spec.md + tasks.md 全部完成之后，最后一步撰写独立的测试计划文件。

写入 `.knowledge-work/<change>/unit-task.md`，必须包含以下 4 个章节：

```markdown
# Unit Test Plan — <change>

## 第 1 章：测试范围分析

基于 spec.md 第 2 章的 capability 清单和第 7 章中标注为「单元测试」的场景，确定测试范围。

### 被测类清单
| 被测类 | 来源（spec 章节 + 源码位置） | 说明 |
|--------|---------------------------|------|
| <ClassName> | spec 第 2 章 Capability 1 / <文件路径:行号> | <简要说明> |

### 排除范围
- <排除的类/方法>：<排除理由>（如纯 UI 类、仅做数据传递的 POJO 等）

## 第 2 章：测试任务清单

按被测类分组，每个被测类对应一个测试任务块。

### <被测类名>（新增/修改）

- **被测类**：<完整类名>（如 com.xiaomi.minigame.xxx.XxxClass）
- **测试类名**：<被测类名>Test
- **测试文件路径**：`<模块>/src/test/java/<包路径>/<TestClass>.java`
- **测试类型**：JVM 单元测试 / Instrumented 测试
  - 选择理由：<如「被测方法为纯逻辑，无 Android 依赖 → JVM」>
- **测试方法清单**：

| 测试方法名 | 场景类型 | 场景编号 | 断言策略 |
|-----------|---------|---------|---------|
| <方法名>_<条件>_<预期结果> | core | T-01 | 返回值验证（assertEquals） |
| <方法名>_<条件>_<预期结果> | boundary | T-02 | 异常验证（assertThrows） |
| <方法名>_<条件>_<预期结果> | unexpected | T-03 | 行为验证（verify） |

- **Mock 依赖清单**：

| 依赖类 | Mock 方式 | 说明 |
|--------|----------|------|
| <DependencyClass> | mock / mockStatic / spy | <使用 Mockito/MockedStatic> |

## 第 3 章：覆盖度自检矩阵

| 被测类 | public 方法 | core | boundary | unexpected | regression |
|--------|------------|------|----------|------------|------------|
| <ClassName> | methodA | <测试方法名> | <测试方法名> | <测试方法名> | N/A |
| <ClassName> | methodB | <测试方法名> | <测试方法名> | <测试方法名> | N/A |

- 每行至少 core + boundary + unexpected 都有覆盖（✓ 或填写测试方法名）
- 无覆盖时填写「-」并标注理由
- bugFix 时 regression 列必须有覆盖，非 bugFix 场景标注「N/A」

## 第 4 章：测试依赖配置

### <目标模块> build.gradle

| 依赖 | 配置类型 | 状态 |
|------|---------|------|
| junit:junit:4.13.2 | testImplementation | 已有 / 新增 |
| org.mockito:mockito-core:x.x.x | testImplementation | 已有 / 新增 |
| org.mockito:mockito-inline:x.x.x | testImplementation | 已有 / 新增 |
| androidx.test:runner:x.x.x | androidTestImplementation | 已有 / 新增 |

### 配置步骤（如需新增依赖）
- <具体需要在 build.gradle 中添加的配置>
```

unit-task.md 撰写依赖说明（撰写顺序不可提前）：
- spec.md 第 2 章的 capability 定义 → 确定被测范围
- spec.md 第 7 章的测试场景和编号 → 确定测试目标
- tasks.md 的文件清单 → 确定被测类的精确路径

### 步骤 6：自检验证

创建完四个制品后，执行自检：

```
--- design.md ---
- [ ] 每个 Decision 有 rationale 和 rejected alternative
--- spec.md ---
- [ ] 包含全部 8 个章节标题
- [ ] 第 2 章无模糊描述（如"应该正常工作"）
- [ ] 第 7 章每个场景使用了唯一编号（T-xx 格式）
- [ ] 第 7 章每个场景标注了优先级（P0/P1/P2）和验证方式（「单元测试」或「手动验证」）
- [ ] 第 7 章至少有 1 个 P0/P1 场景标注为「单元测试」
- [ ] 第 7 章不包含测试实现细节（测试类名、方法名、Mock 策略、断言策略、文件路径、测试类型）
--- tasks.md ---
- [ ] 文件清单与 spec.md 第 5 章一致（交叉比对）
- [ ] task 总数合理（bugFix 2-4, 需求 5-7）
- [ ] 只包含 Investigation / Core Implementation / Verification 三个阶段
- [ ] 不包含 Unit Testing 阶段或任何测试编写 task
--- unit-task.md ---
- [ ] 包含 4 个章节（测试范围分析 / 测试任务清单 / 覆盖度自检矩阵 / 测试依赖配置）
- [ ] 每个测试任务块七要素齐全（被测类、测试类名、测试文件路径、测试类型+选择理由、测试方法清单、Mock 依赖清单、断言策略）
- [ ] 测试方法使用三段式命名（<方法名>_<条件>_<预期结果>）
- [ ] 每个测试方法标注了场景类型（core/boundary/unexpected/regression）和对应的 spec 第 7 章场景编号
- [ ] 覆盖度矩阵中每个被测类的每个 public 方法至少覆盖 core + boundary + unexpected 三类场景
- [ ] bugFix 时覆盖度矩阵 regression 列非空
- [ ] spec.md 第 7 章标注为「单元测试」的 P0/P1 场景编号全部在 unit-task.md 中出现（交叉比对）
--- 通用 ---
- [ ] 不违反任何架构约束
- [ ] 涉及 Bridge API 修改时有独立文档更新 task
```

不通过的项目立即修正后再返回。

### 步骤 7：返回结果

向 Orchestrator 返回：
1. **方案摘要**（3-5 行）
2. **任务数量**（业务 task 数 + 测试 task 数分别列出）
3. **预估影响范围**（涉及文件数 + 预估新增/修改行数）
4. **制品清单**：确认 design.md / spec.md / tasks.md / unit-task.md 已创建

---

## 硬性约束

```
[ASSERT-DISALLOW] 禁止写代码或修改源码
[ASSERT-DISALLOW] 禁止运行构建命令
[ASSERT-DISALLOW] 禁止修改 knowledge/current/ 下的任何文档
[DISALLOW]        知识库与源码矛盾时以源码为准，在 design.md 中标注差异
[DISALLOW]        禁止绕过 exploration.md 直接从原始需求设计
[DISALLOW]        禁止通过 task_id 复用其他阶段的子 Agent 会话
```

---

## CR 打回迭代

```
1. 在 design.md 中标注版本号（v2/v3）
2. Decisions 章节记录本次变更原因和 CR 反馈要点
3. 推荐顶部维护变更日志
```

---

## 完成标准

Orchestrator 逐项验证（完整清单见 `roles/planner.md` 末尾），核心项：

--- 文件存在性 ---
- [ ] design.md / spec.md / tasks.md / unit-task.md 四文件存在且非空

--- design.md ---
- [ ] 包含 Context/Goals/Non-Goals/Decisions/Risks 五章节
- [ ] 每个 Decision 附 rationale + rejected alternative
- [ ] 涉及重构时 Context 引用了历史快照

--- spec.md ---
- [ ] 包含全部 8 个章节标题，不适用章节标注了理由
- [ ] 第 1 章标注了输入来源（口头/PRD/Jira）
- [ ] 第 2 章使用 GIVEN/WHEN/THEN 格式，无模糊描述
- [ ] 第 6 章每个功能点至少 1 条可验证 AC
- [ ] 第 7 章每个场景使用唯一编号（T-xx 格式）
- [ ] 第 7 章每个场景标注优先级（P0/P1/P2）和验证方式（「单元测试」或「手动验证」）
- [ ] 第 7 章至少有 1 个 P0/P1 场景标注为「单元测试」
- [ ] 第 7 章不包含测试实现细节（Orchestrator 用 Grep 检查不应出现 "Test.java" "mockStatic" "assertEquals" 等关键词）
- [ ] bugFix 时第 2 章包含回归场景

--- tasks.md ---
- [ ] 每个 task 标注操作类型 + 精确文件路径 + 规模预估
- [ ] 文件清单与 spec.md 第 5 章一致
- [ ] 每个 task 对应 commit 粒度（完成后可编译）
- [ ] 只包含 Investigation / Core Implementation / Verification 三个阶段
- [ ] 不包含 Unit Testing 阶段或任何测试编写 task（Orchestrator 用 Grep 检查不应出现 "Unit Testing" "测试类" "Test.java" 等关键词）
- [ ] 涉及 Bridge API 修改时有独立文档更新 task

--- unit-task.md ---
- [ ] 包含 4 个章节：测试范围分析 / 测试任务清单 / 覆盖度自检矩阵 / 测试依赖配置
- [ ] 每个测试任务块七要素齐全（被测类、测试类名、测试文件路径、测试类型+选择理由、测试方法清单、Mock 依赖清单、断言策略）
- [ ] 每个测试任务块标注了操作类型（「新增」或「修改」）
- [ ] 测试方法使用三段式命名 + 场景类型标注 + 场景编号追溯
- [ ] 覆盖度矩阵每个被测类的 core/boundary/unexpected 都有覆盖
- [ ] bugFix 时 regression 列非空
- [ ] spec.md 第 7 章标注为「单元测试」的 P0/P1 场景编号全部在 unit-task.md 中出现（Orchestrator 交叉比对）
- [ ] 测试依赖配置标注了「已有」/「新增」状态

--- 返回值 ---
- [ ] 返回了摘要/任务数量（业务 task 数 + 测试 task 数）/影响范围
- [ ] 源码引用标注了来源（文件名:行号）
