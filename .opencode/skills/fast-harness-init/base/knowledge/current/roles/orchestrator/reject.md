# CR2 打回分级（阶段 6 回退）

CR2 是人工底线门禁。用户在 CR2 发现问题时，根据问题严重程度选择不同的回退深度：

| 打回级别 | 命令 | 回退目标 | 适用场景 |
|---------|------|---------|---------|
| **fix** | `/pipeline:reject fix` | → 阶段 4（Builder） | 实现细节有问题，方案本身没问题 |
| **redesign** | `/pipeline:reject redesign` | → 阶段 2（Propose） | 方案/设计有缺陷，需重新设计 |
| **rethink** | `/pipeline:reject rethink` | → 阶段 1（Explore） | 需求理解有误，需要重新探索 |

## 回退协议

```text
[MUST-DO] CR2 阶段打回时，Orchestrator 必须：
1. 判定打回级别：
   a) fix（回 Builder）— 可由 Orchestrator 自主判定，无需用户确认：
      - 用户反馈明显属于实现细节修正（如"加 null check"、"日志级别改一下"、
        "变量名不对"、"缺少边界判断"等）
      - 不涉及接口变更、流程变更、架构调整
      - Orchestrator 直接回退到阶段 4，告知用户即可
   b) redesign / rethink（回 Propose 或 Explore）— 必须用户确认或用户主动指定：
      - 用户显式说"重新设计"/"方案有问题"/"需求理解错了" → 推断级别后向用户确认
      - Orchestrator 自己判断需要 redesign/rethink → 必须建议并等待用户确认
      - [ASSERT-DISALLOW] 禁止 Orchestrator 自主决定 redesign/rethink，必须有用户明确同意
2. 根据级别回退到对应阶段：
   - fix → currentStage=4，保留 design.md/spec.md/tasks.md，Builder 修复后重走 5→6
   - redesign → currentStage=2，保留 exploration.md，清除 design.md/spec.md/tasks.md/unit-task.md，
     Planner 重新设计后走 3→4→5→6（CR1 是否需要由 Tier 决定）
   - rethink → currentStage=1，清除所有设计产出文件，从探索重新开始
3. 在 history 中记录 {"action": "rejected", "level": "<级别>", "reason": "<用户给出的原因>"}
4. 更新 progress.md 记录回退决策和原因
5. Tier 重新评估：redesign/rethink 回退后，若问题揭示了更大的复杂度，
   Orchestrator 应建议用户升级 Tier（仍需用户确认）
```

## 产出文件保留策略

| 回退级别 | exploration.md | design.md | spec.md | tasks.md | unit-task.md | 已写代码 |
|---------|---------------|-----------|---------|----------|-------------|---------|
| fix | 保留 | 保留 | 保留 | 保留 | 保留 | 保留（待修改） |
| redesign | 保留 | **清除** | **清除** | **清除** | **清除** | **回滚** |
| rethink | **清除** | **清除** | **清除** | **清除** | **清除** | **回滚** |

代码回滚方式：`git reset --mixed` 到 Builder 开始前的 commit（从 history 中获取 Builder 进入时的 commit hash）。
