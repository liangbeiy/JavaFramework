---
name: "complexity-score"
description: "变更复杂度评分（delta-熵增评估）。在 Verifier 阶段首步执行，或在 Explorer/Planner 阶段预评估变更影响面时使用。"
---

# 变更复杂度评分（delta-熵增评估）

基于信息熵理论，对本次变更的复杂度进行五维量化评分。不依赖编译/AST/SonarQube，纯基于 git diff + 路由索引，秒级完成。

## 触发条件

- **Verifier 阶段步骤 1**：自动执行，作为第一道门禁
- **Explorer/Planner 阶段**：手动调用预评估变更影响面，辅助方案设计
- **人工 CR 阶段**：Orchestrator 展示评分明细供审查参考

---

## 理论基础

### 核心概念

**delta-熵增**（ΔS）：单次变更前后系统复杂度的变化量。关注的不是静态绝对值，而是"这次改动让系统变复杂了多少"。

### 五维评分模型

```
ΔS = S_scale + S_coupling + S_spread + S_cycle + S_cognitive
```

| 维度 | 度量什么 | 数据来源 | 归一化上限 |
|------|---------|---------|-----------|
| S_scale | 变更规模——改了多少行/多少文件 | `git diff --numstat` | 3.0 |
| S_coupling | 耦合度——跨模块依赖有多复杂 | `call-graph.tsv` 跨模块边 | 3.0 |
| S_spread | 扩散度——散布在几个模块里 | 文件路径提取模块 | 2.0 |
| S_cycle | 循环依赖——有没有模块间双向依赖 | `module-deps.tsv` | 无上限(×5/个) |
| S_cognitive | 认知熵增——团队认知负担多大 | 新增文件占比 | 2.0 |

### 归一化公式

```
S_scale    = min(行数变化/500 + 源码文件数/10, 3.0)
S_coupling = min(跨模块调用边数/5, 3.0)
S_spread   = min((涉及模块数-1)/3, 2.0)
S_cycle    = 循环依赖对数 × 5.0              ← 无上限，架构硬伤
S_cognitive = min(新增文件占比 × 2, 2.0)
```

### 风险分级

| ΔS 范围 | 风险等级 | 退出码 | Verifier 处理 |
|---------|---------|--------|-------------|
| ΔS ≤ 2 | 低风险 | 0 | 自动通过 |
| 2 < ΔS ≤ 5 | 中风险 | 0 | 通过，记录到 progress.md |
| 5 < ΔS ≤ 10 | 高风险 | 2 | 通过，人工 CR 需说明熵增理由 |
| ΔS > 10 | 极高风险 | 1 | **阻断**，必须拆分变更 |

---

## 执行协议

### 作为 Verifier 步骤 1 执行

```
1. 执行 capability:complexity-score 脚本
   Bash: .opencode/scripts/harness/complexity-score.sh（timeout: 60000ms）

2. 解析输出中的 COMPLEXITY_SCORE 和 RISK_LEVEL

3. 根据风险等级决定后续流程：
   - low / medium → 继续步骤 2（编译验证）
   - high → 继续步骤 2，但在返回摘要中标注「高风险：ΔS=X.X」
   - critical → 立即停止，报告 Orchestrator

4. 将五维明细和风险等级写入 progress.md：
   格式：
   - 变更复杂度评分：ΔS=X.X（<风险等级>）
     - S_scale=X.X, S_coupling=X.X, S_spread=X.X, S_cycle=X.X, S_cognitive=X.X
```

### 作为 Explorer/Planner 预评估

在设计阶段调用时，侧重 S_coupling 和 S_spread 两个维度，辅助判断：
- 方案涉及的模块是否过多（S_spread 高）→ 考虑拆分
- 跨模块调用是否过于复杂（S_coupling 高）→ 考虑引入接口隔离
- 是否存在循环依赖风险（S_cycle 非零）→ 设计阶段就应消除

```
1. 手动执行:
   Bash: .opencode/scripts/harness/complexity-score.sh

2. 读取输出中的五维明细

3. 在 design.md 的 Risks 章节中记录预评估结果：
   - 预估复杂度：ΔS ≈ X.X（<风险等级>）
   - 高风险维度：<说明哪些维度偏高及缓解措施>
```

### 作为人工 CR 参考

Orchestrator 在阶段 6（代码 CR）向用户展示评分明细：

```
## 变更复杂度评分

ΔS = X.X（<风险等级>）

| 维度 | 分数 | 说明 |
|------|------|------|
| S_scale | X.X | N 个文件，+M -K 行 |
| S_coupling | X.X | N 条跨模块调用边 |
| S_spread | X.X | 涉及 N 个模块 |
| S_cycle | X.X | N 对循环依赖 |
| S_cognitive | X.X | N/M 为新增文件 |
```

---

## 极高风险（ΔS > 10）的处理

当评分超过 10 时，**不是 Builder 的代码质量问题，是方案粒度问题**：

```
1. Verifier 立即停止后续步骤，向 Orchestrator 报告：
   - ΔS 总分和五维明细
   - 建议：拆分为多个小变更，或优化方案降低跨模块影响

2. Orchestrator 通知用户和 Builder：
   - 不计入连续失败次数（与编译失败等代码问题区分）
   - 选项 A：拆分 tasks.md，将当前变更拆为 2-3 个子变更
   - 选项 B：优化 design.md，减少跨模块依赖
   - 选项 C：用户审批豁免（需在 progress.md 记录豁免理由）

3. 豁免机制：
   - 用户在人工 CR 中明确说「豁免复杂度评分」→ Orchestrator 记录到 progress.md
   - 下次 Verifier 重跑时，Orchestrator 在 prompt 中传入 --skip-complexity 参数
   - 豁免仅对当前变更有效，不影响后续变更
```

---

## 与其他 capability 的关系

| capability | 关系 |
|-----------|------|
| `impact-analysis` | complexity-score 的 S_coupling 数据来自 call-graph（与 impact-analysis 同源） |
| `dep-direction-verify` | complexity-score 的 S_cycle 检测循环依赖（与 dep-verify 的反向依赖互补） |
| `route-query` | Explorer/Planner 预评估时可先用 route-query 定位，再用 complexity-score 量化 |
| `build-verify` | complexity-score 放在 build-verify 之前（不依赖编译，秒级完成） |

---

## 评分模型的校准

当前权重和归一化参数是初始值，团队应根据实际使用数据校准：

### 校准方法

```
1. 积累 20+ 次变更的评分数据（progress.md 中有记录）
2. 对比评分与人工 CR 的实际反馈：
   - 评分低但 CR 打回率高 → 某个维度权重不足
   - 评分高但 CR 顺利通过 → 某个维度权重过高或上限过低
3. 调整归一化分母和上限：
   - 如果 S_scale 频繁打满 3.0 → 提高分母（如 500→800）
   - 如果 S_coupling 从不超过 1.0 → 降低分母（如 5→3）
```

### 未来扩展（接入 SonarQube 后）

```
S_static_precise = w1·norm(ΔCBO) + w2·norm(ΔRFC) + w3·norm(ΔLCOM3) + w4·norm(ΔWMC)
                 + w5·norm(ΔDIT) + w6·norm(ΔNOC)

替换关系：
- S_coupling → norm(ΔCBO) + 0.2·norm(ΔRFC)（精确替代）
- S_cognitive → 2·norm(ΔLCOM3) + 0.1·norm(ΔWMC)（精确替代）
- S_scale / S_spread / S_cycle 保持不变（SonarQube 不提供这些维度）
```

---

## 约束

```
[MUST-DO] 作为 Verifier 步骤 1 执行时，不论结果如何都必须记录五维明细到 progress.md
[MUST-DO] 极高风险阻断不计入连续失败次数
[MUST-DO] 豁免必须由用户明确授权并记录理由
[DISALLOW] 禁止自行降低阈值或跳过评分步骤
[DISALLOW] 禁止修改脚本中的归一化参数（校准由团队统一决定）
[INFO] 路由索引缺失时自动降级：S_coupling=0, S_cycle=0（仅影响这两个维度）
[INFO] 无源码变更时（如纯文档/配置修改）：ΔS 只有 S_scale 一个维度有值，通常 ≤ 1.0
```
