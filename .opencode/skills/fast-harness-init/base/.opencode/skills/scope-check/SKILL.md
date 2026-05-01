---
name: "scope-check"
description: "改动范围检查。比对技术方案声明的修改范围与实际 Diff，检测越界修改。Invoke when user asks to check scope or 检查改动范围。"
---

# scope-check

改动范围检查。比对技术方案声明的修改范围与实际 Diff，检测越界修改。

## 触发条件

- Builder 阶段完成后，Verifier 阶段前自动执行
- 用户手动要求 "检查改动范围"

## 与 Verifier 步骤 7（方案实现对比验证）的关系

scope-check L3 和 Verifier 步骤 7 维度 A 都涉及 tasks.md 文件清单与 git diff 的比对，但侧重不同：

| 检查项 | scope-check L3 | Verifier 步骤 7 |
|--------|---------------|----------------|
| 声明有 + 实际有 | ✅ 符合 | ✅ 符合 |
| 声明有 + 实际无 | ⚠️ 未实现 | ❌ 验证失败（打回 Builder） |
| 声明无 + 实际有 | ❌ 越界修改 | 不检查（由 scope-check 负责） |
| spec.md 功能/接口对齐 | 不检查 | ✅ 维度 B/C/D |

**分工**：scope-check 侧重**防越界**（L3 安全网），Verifier 步骤 7 侧重**防遗漏 + 方案对齐**（质量验证）。两者互补，"声明有/实际无"的重复检查是有意为之——scope-check 作为早期软警告，Verifier 作为门禁硬判定。

---

## 四层纵深防御体系

本 Skill 属于 **L3 Diff 审核层**，与其他层协同：

| 层级 | 名称 | 时机 | 机制 |
|------|------|------|------|
| L1 | 方案声明层 | 开发前 | tasks.md 声明修改文件列表 + Approval Gate |
| **L2** | **实时拦截层** | **开发中** | **PreToolUse Hook + scope_whitelist.txt** |
| **L3** | **Diff 审核层** | **开发后** | **本 Skill：比对方案声明 vs 实际 Diff** |
| L4 | Review 验证层 | 合入前 | 人工 + Agent CR |

---

## scope_whitelist.txt

Builder 阶段启动时，Orchestrator 从 tasks.md 提取修改文件列表，写入：

```
.knowledge-work/<change>/scope_whitelist.txt
```

格式（每行一个文件路径或通配符）：
```
GameRuntimeLib/src/main/java/com/xiaomi/minigame/runtime/GameRuntimeDelegate.java
GameCoreLib/src/main/java/com/xiaomi/minigame/core/**
app/src/main/res/values/arrays.xml
```

---

## L2 实时拦截（Hook）

在 `harness-hooks.ts` 的 `tool.execute.before` 中：

```
当 Builder 角色执行 edit/write 时：
1. 读取 scope_whitelist.txt
2. 检查目标文件是否在白名单内
3. 不在白名单 → 警告并记录
4. .knowledge-work/ 和 knowledge/ 下的文件免检
```

---

## L3 Diff 审核（本 Skill 核心）

### 执行步骤

```
1. 读取 .knowledge-work/<change>/tasks.md — 提取声明的修改文件列表
2. 执行 git diff --name-only — 获取实际变更文件列表
3. 比对两个列表：
   - 声明有、实际有 → ✅ 符合
   - 声明有、实际无 → ⚠️ 未实现（可能遗漏）
   - 声明无、实际有 → ❌ 越界修改（需要审查）
4. 产出范围检查报告
```

### 报告格式

```markdown
# 改动范围检查报告

## 符合声明的修改
- ✅ <文件路径>（Task X.X）

## 未实现的声明
- ⚠️ <文件路径>（Task X.X 声明但未修改）

## 越界修改
- ❌ <文件路径>（未在 tasks.md 中声明）
  - 修改内容摘要：...

## 结论
- [ ] 所有修改均在声明范围内
- [ ] 存在 N 处越界修改，需要审查
```

---

## scope-expand 扩展流程

当 Builder 发现需要修改声明外的文件时：

```
1. Builder 向 Orchestrator 报告：需要扩展修改范围
2. Orchestrator 更新 tasks.md 添加新 task
3. Orchestrator 更新 scope_whitelist.txt
4. 通知用户确认
5. 用户 approved 后 Builder 继续
```

---

## 约束

```
[MUST-DO] scope_whitelist.txt 由 Orchestrator 生成，Builder 不得自行修改
[MUST-DO] 越界修改必须在报告中逐条列出
[DISALLOW] 禁止自动批准越界修改
```
