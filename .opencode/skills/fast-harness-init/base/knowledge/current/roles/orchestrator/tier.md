# Tier 分级流水线

变更按规模分为三档（Tier），不同 Tier 走不同阶段子集，避免小改动承受全量流程。

## Tier 定义

| Tier | 名称 | 适用场景 | 文件上限 | 新增类上限 | 模块上限 |
|------|------|---------|---------|----------|---------|
| 1 | Trivial | NPE 修复、日志调整、配置变更、typo | ≤ 3 | 0 | 1 |
| 2 | Standard | 功能增强、单模块 bug、小型重构 | ≤ 10 | ≤ 3 | 1 |
| 3 | Full | 新特性、跨模块改动、架构变更 | 不限 | 不限 | 不限 |

## 不可降级条件（硬指标）

满足以下**任一条件**即强制 Tier 3，不可降级：
- 新增/修改 public API 且跨模块被调用
- 改动涉及 2+ 个 Gradle module
- 涉及 AIDL / IPC / `IPCSingletonManager`
- 涉及初始化链（`MApplication` / `QuickGamePlatform` / `ApplicationPlatform` / `QuickGameCore`）
- 涉及 Architecture Constraints 中列出的组件

满足以下**任一条件**即强制至少 Tier 2：
- 改动文件 > 2 个
- 新增类 ≥ 1 个
- 涉及 Bridge API
- 非纯 bugfix（有功能变更语义）

## Tier 阶段跳过表

| 阶段 | Tier 1 | Tier 2 | Tier 3 |
|------|--------|--------|--------|
| 1. Explore | 跳过 | 跳过（内联到 Propose） | 必须 |
| 2. Propose | 跳过 | 必须（精简版：spec + tasks，无 design.md） | 必须 |
| 3. CR1 | 跳过 | 可选（用户决定） | 必须 |
| 4. Build | **必须** | **必须** | **必须** |
| 5. Verify | **必须** | **必须** | **必须** |
| 6. CR2 | **必须** | **必须** | **必须** |
| 7. Commit | **必须** | **必须** | **必须** |
| 8. Archive | 跳过（仅记录到 progress.md） | 必须（轻量归档） | 必须（完整归档） |

## Tier 判定协议

```text
[ASSERT-DISALLOW] /pipeline:new 创建变更时，必须执行以下 Tier 判定协议：
1. 调用 pipeline-guard 工具: action="tier-validate", target="<变更描述>"
2. 获取脚本返回的最低 Tier
3. Orchestrator 可在脚本结果基础上向上调整（不可向下）
4. 向用户展示: Tier 结论 + 判定理由 + 阶段计划
5. 用户确认（用户只能维持或升级 Tier，不可降到低于脚本判定的值）
6. 将 tier / tierReason / tierConfirmedByUser 写入 pipeline-state.json
7. 调用 pipeline-guard 工具: action="tier-check" 验证写入成功
未完成此协议禁止进入任何阶段。
```

## Tier 溢出检查（事后校验）

```text
[ASSERT-DISALLOW] Builder 子 Agent 返回后，进入 Verify 前，必须执行：
1. 调用 pipeline-guard 工具: action="diff-audit"
2. 若返回 FAIL（溢出）→ 强制升级 Tier，补做缺失阶段
3. 若返回 PASS → 正常推进
忽略 FAIL 结果继续推进等同于跳过门禁。
```

## 阶段跳过守卫

```text
[ASSERT-DISALLOW] 跳过任何阶段前，必须执行：
1. 调用 pipeline-guard 工具: action="stage-skip", target="<阶段号>"
2. 若返回 FAIL → 禁止跳过，必须执行该阶段
3. 若返回 PASS → 在 history 中记录 {"action": "skipped", "reason": "tier-N-exempt"}
未经验证的阶段跳转等同于跳过门禁。
```

## pipeline-state.json 中 Tier 相关字段

```json
{
  "activeChange": {
    "name": "fix-xxx",
    "tier": 1,
    "tierReason": "单文件NPE修复，无public API变更，无跨模块",
    "tierConfirmedByUser": true,
    "currentStage": 4
  }
}
```
