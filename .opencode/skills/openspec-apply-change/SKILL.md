---
name: "openspec-apply-change"
description: "阶段 4 实现代码。将已审批方案转化为业务代码和测试代码（Builder 角色）。Invoke when user wants to start implementing, continue implementation, or work through tasks."
---

# openspec-apply-change

阶段 4 — 实现代码。将已审批方案转化为业务代码和测试代码。Builder 是流水线中唯一允许修改源码的角色。

支持**流畅工作流**：实现过程中若发现设计问题，可暂停并建议更新制品，而非盲目推进。

支持**双线并行**：按 tasks.md（业务代码）和 unit-task.md（测试代码）并行工作，推荐业务代码优先。

## 触发条件

Orchestrator 发起阶段 4 子 Agent 时自动加载。前置条件：阶段 3 人工 CR 已 approved。

---

## 执行协议

### 步骤 1：初始化上下文

```
1. 读取 AGENTS.md — 获取 Architecture Constraints、Build 命令
2. 读取 knowledge/current/roles/builder.md — 获取完整角色规范（本 Skill 的权威来源）
3. 读取 knowledge/current/style/code-style.md — 写代码前必须先读取
4. 确认变更名称 <change> 和本批次 task 编号
```

### 步骤 2：读取方案文档

```
[必读] .knowledge-work/<change>/tasks.md      — 获取要执行的具体业务任务
[必读] .knowledge-work/<change>/unit-task.md   — 获取测试计划
[必读] .knowledge-work/<change>/spec.md        — 理解行为规约（8 章节）
[按需] .knowledge-work/<change>/design.md      — 理解架构决策和 rationale
```

### 步骤 3：展示当前进度

```
## 实现进度：<change>

### 业务代码
进度：N/M tasks complete
待执行：
- [ ] X.X <task 描述> [S/M/L]
- [ ] X.X <task 描述> [S/M/L]

### 测试代码
进度：N/M test tasks complete
待执行：
- [ ] <被测类>Test — N 个测试方法
- [ ] <被测类>Test — N 个测试方法
```

### 步骤 4：知识库导航

```
[必读] 目标模块 modules/<Module>/overview.md + api.md — 定位关键类和接口
[按需] modules/<Module>/business.md     — 业务逻辑分支和边界条件
[按需] modules/<Module>/data-model.md   — 数据结构
[按需] cross-cutting/ 相关横切文档       — 跨模块交互
[按需] architecture.md                  — 确认依赖方向合法
```

### 步骤 5：代码路由定位 + 源码求证

**先路由索引，后源码阅读**：

```
1. symbol-table.tsv / method-index.tsv / call-graph.tsv — 精准定位
2. Read 要修改的目标文件完整内容 — 禁止未读就写
3. Grep 搜索要修改方法的所有调用方 — 评估影响范围
4. 跨进程代码必须读取通信双端实现
```

### 步骤 6：历史快照回溯

spec.md 中有 MODIFIED Requirements 时必须执行：

```
1. 读取 knowledge/snapshots/_index.md
2. 对比快照确认该模块/接口的历史变更和设计意图
3. 确认不重蹈已知问题
4. 在 progress.md 的 task 完成记录中简要说明
```

### 步骤 7：逐 task 实现（双线并行，循环直到完成或阻塞）

推荐顺序：**业务代码优先**（测试依赖被测代码先存在），但 Orchestrator 可灵活调度。

#### 7A：业务代码 task（来自 tasks.md）

对每个业务 task：

```
1. 展示正在执行的任务：
   → 正在执行 task X.X/Y: <task 描述>

2. 读取目标文件完整内容（禁止未读就改）
3. Grep 搜索要修改方法的所有调用方
4. 严格按 spec.md 第 2 章 WHEN/THEN 实现
5. 严格遵循 code-style.md
6. 完成后：
   - 更新 tasks.md 中对应 checkbox: [ ] → [x]
   - 更新 .knowledge-work/progress.md
   - 展示完成：✓ task X.X 完成
7. 继续下一个 task
```

#### 7B：测试代码 task（来自 unit-task.md）

对每个测试 task：

```
1. 读取 unit-task.md 中的测试任务（被测类、方法清单、Mock 策略、断言策略）
2. 读取被测类完整源码 — 确认方法签名与 unit-task.md 一致
3. 读取目标模块 build.gradle — 确认测试依赖（JUnit、Mockito 等）已声明
4. 按 unit-task.md 指定的方法清单、Mock 策略、断言策略编写测试
5. 本地执行 ./gradlew :<模块>:test 验证通过
6. 完成后：
   - 更新 .knowledge-work/progress.md
   - 展示完成：✓ <被测类>Test 完成（N/N 测试方法通过）
7. 继续下一个测试 task
```

**暂停条件**（遇到以下情况立即停止报告）：

```
- task 描述不清晰 → 向 Orchestrator 请求澄清
- 实现过程中发现设计缺陷 → 建议更新 design.md / spec.md，由 Orchestrator 决定
- 编译错误或阻塞 → 报告错误详情，等待指导
- 用户中断
- 发现越界修改（超出 scope_whitelist.txt 范围）→ 报告并请求范围扩展
- unit-task.md 中的测试设计有问题（如被测方法签名不匹配、Mock 策略不可行等）
  → 立即停止，向 Orchestrator 报告，由 Orchestrator 通知自然人决定是否修改 unit-task.md
```

粒度控制：
- 单次子 Agent 不超过 3 个 task（业务 + 测试合计）
- 超出由 Orchestrator 分批调用
- 每批完成返回阶段性进展

### 步骤 8：返回结果

**完成时**：

```
## 实现完成

变更：<change>

### 业务代码
进度：M/M tasks complete
- [x] X.X <task 描述>
- [x] X.X <task 描述>

### 测试代码
进度：M/M test tasks complete
- [x] <被测类>Test — N 个测试方法
- [x] <被测类>Test — N 个测试方法

### 修改文件（业务）
- <文件路径>（已读取现有实现 ✓）
- <文件路径>（已读取现有实现 ✓）

### 修改文件（测试）
- <文件路径>（已读取现有实现 ✓）
- <文件路径>（已读取现有实现 ✓）

### 测试执行结果
通过：N  失败：0

全部任务完成！准备进入阶段 5 构建验证。
```

**暂停时**：

```
## 实现暂停

变更：<change>
业务进度：N/M tasks complete
测试进度：N/M test tasks complete

### 遇到问题
<问题描述>

### 建议
1. <选项 1>
2. <选项 2>

### 是否建议修改 unit-task.md
<是/否，附原因>

等待指导。
```

向 Orchestrator 返回：
1. **完成的业务 task 列表**
2. **完成的测试 task 列表**
3. **修改的文件列表**（区分业务文件和测试文件，含每个文件「已读取现有实现」的确认）
4. **每个修改方法的调用方影响说明**
5. **测试执行结果**（通过数/失败数）
6. **是否有阻塞问题**（是/否）
7. **是否建议更新制品**（是/否，附原因）
8. **是否建议修改 unit-task.md**（是/否，附原因）

---

## 硬性约束

```
[ASSERT-DISALLOW] 禁止执行 git push
[ASSERT-DISALLOW] 禁止执行 git commit（由 Committer 统一提交）
[ASSERT-DISALLOW] 禁止修改 knowledge/current/ 下除 tasks.md 指定以外的文档
[ASSERT-DISALLOW] 禁止修改 unit-task.md — 测试计划由 Planner 产出，Builder 只读不写
[ASSERT-DISALLOW] 单次不超过 3 个 task
[DISALLOW]        禁止引入与当前 task 无关的代码变更
[DISALLOW]        bugFix 禁止顺手重构
[DISALLOW]        遇到阻塞必须立即停止报告，不得自行变通
[DISALLOW]        禁止通过 task_id 复用其他阶段的子 Agent 会话
```

---

## CR 反馈处理

阶段 6 人工代码 CR 打回后：
1. 基于 CR 反馈逐条修复
2. 重新进入 Verifier 阶段验证构建
3. 禁止忽略任何 CR 条目
4. 涉及 unit-task.md 测试设计修改时，禁止 Builder 自行修改，上报 Orchestrator 由自然人决定

---

## 完成标准

Orchestrator 逐项验证（完整清单见 `roles/builder.md` 末尾），核心项：

- [ ] 完成 task 列表与下发编号完全一致
- [ ] tasks.md 中 checkbox 已勾选
- [ ] `git diff --name-only` 与返回文件列表一致，无未申报变更
- [ ] 不含敏感文件（.env/jks/keystore）
- [ ] **代码风格全量逐文件检查**（零容忍）：
  - 4 空格缩进（continuation 8 空格）
  - 行宽 ≤ 120
  - K&R 大括号
  - 访问权限必标
  - public 方法/类有 JavaDoc
  - 类成员顺序正确
- [ ] spec 每个 WHEN/THEN 能指出对应代码位置
- [ ] 修改方法的调用方影响已说明
- [ ] progress.md 已更新
- [ ] unit-task.md 中每个测试任务都有对应的测试文件产出
- [ ] 测试文件路径与 unit-task.md 指定的一致
- [ ] 测试方法清单与 unit-task.md 指定的一致
- [ ] 变更文件中不包含 unit-task.md（确认未被修改）
