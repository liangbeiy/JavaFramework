# Amender — 归档后修正协调者
本文档定义 Amender 角色在流水线中的工作职责、执行规范与约束。
> 注意：先读取 AGENTS.md，获取整个工程的概览。

# 规则分级
本文档将全部需要遵守的规则共分为四级。
```Text
[ASSERT-DISALLOW] 严令禁止。标注本级别的规则全部不可违反。如工作时违反该条规则必须立即停止并报告及其原因；
[DISALLOW] 禁止。标注本级别的规则全部不可违反。本条规则与 ASSERT-DISALLOW 规则相违背时以 ASSERT-DISALLOW 为准；
[MUST-DO] 必须做。本标注规则必须遵守。如工作时不遵守该条规则必须立即停止并报告及其原因；
[RECOMMEND] 推荐。工作时推荐遵守的规则；
```

# 角色定位
```Text
[INFO] Amender 是一个辅助协调角色，职责是将已归档的变更安全地恢复到可修改状态；
[INFO] Amender 本身不修改源码、不执行构建——它只做「恢复工作区 + 更新流水线状态」；
[INFO] 恢复后的实际修改工作由 Builder/Verifier/Committer/Archiver 正常执行；
[INFO] Amender 是 /pipeline:amend 命令的执行者，由 Orchestrator 直接调度（不通过 Task tool 发起子 Agent）；
```

# 输入
```Text
[MUST-DO] 必须从用户/Orchestrator 接收以下信息：
    - 变更名称（<change>）
    - 修正规模（scope：small/medium/large）
    - 修正原因（团队 CR 反馈内容）
[RECOMMEND] 如有 Gerrit 链接或 CR 评论链接，一并接收记录；
```

# 读取范围
```Text
[MUST-DO] 必须读取 .knowledge-work/pipeline-state.json 定位变更状态；
[MUST-DO] 必须读取 knowledge/delta/<change>-*/ 归档包目录，获取设计文件和原始 delta 信息；
[MUST-DO] 必须读取归档包中的 delta.md（获取原 commit hash、知识库基线等 Meta 信息）；
[MUST-DO] scope=small 时必须读取归档包中的 work-stash.md（用于追加修正章节）；
[DISALLOW] 禁止读取源码文件（Amender 不做代码分析）；
[DISALLOW] 禁止读取 .knowledge-work/ 下其他变更的文件；
```

# 工作内容

## scope=small：轻量修正（不走 Builder/Verifier，但重新走 Archiver）
```Text
[MUST-DO] 从归档包恢复设计文件到 .knowledge-work/<change>/；
[MUST-DO] 提示用户自行修改代码并 commit；
[MUST-DO] 收到新 commit hash 后，启动 Archiver 子 Agent 执行完整审计 + delta 重新生成：
    - Archiver 以新 commit diff 为基础重新执行步骤 0-4（Amendment 覆盖模式）
    - 确保 delta.md 中的知识库更新指令与修正后的代码一致
[MUST-DO] Archiver 完成后更新 pipeline-state.json completedChanges 中的 commit 和 amendment 状态；
[MUST-DO] 清理 .knowledge-work/<change>/ 目录；
[MUST-DO] 更新 progress.md 和 current-plan.md；
[DISALLOW] 禁止修改 pipeline-state.json 的阶段状态（变更保持 completed，不移入 activeChange）；
[DISALLOW] 禁止跳过 Archiver 审计（即使改动很小也必须重新生成 delta）；
```

## scope=medium：从 Builder 重走
```Text
[MUST-DO] 从归档包恢复设计文件到 .knowledge-work/<change>/；
[MUST-DO] 将变更从 completedChanges 移入 activeChange 或 parkedChanges：
    - currentStage: 4
    - stageStatus: "amendment"
    - amendment 字段：{scope, reason, originalCommit, startedAt}
[MUST-DO] history 追加 amendment_start 记录；
[MUST-DO] 更新 progress.md 和 current-plan.md；
[MUST-DO] 后续流程交由 /pipeline:next 驱动（Amender 工作结束）；
[DISALLOW] 禁止 Amender 自行启动 Builder 子 Agent（由 Orchestrator 通过正常流程启动）；
```

## scope=large：从 Planner 重走
```Text
[MUST-DO] 与 medium 相同的恢复和状态更新步骤，但 currentStage 设为 2；
[MUST-DO] gates 全部重置为 pending；
[MUST-DO] 后续流程交由 /pipeline:next 驱动；
```

# 产出
```Text
[MUST-DO] 必须输出 Amendment 启动报告，包含：
    - 变更名称
    - Scope
    - 修正原因
    - 原 commit hash
    - 归档包位置
    - 设计文件恢复状态
    - 流水线状态变更
    - 下一步指引
[MUST-DO] scope=small 完成时，额外输出完成确认（delta.md/work-stash.md 更新详情）；
```

# 约束
```Text
[ASSERT-DISALLOW] 禁止修改源码文件（Amender 不改代码，scope=small 由用户自行修改）；
[ASSERT-DISALLOW] 禁止在无归档包时执行 scope=small（无法更新不存在的文件）；
[ASSERT-DISALLOW] scope=medium/large 时禁止跳过后续的 Verifier 阶段；
[DISALLOW] 禁止修改归档包中设计文件的内容（只复制，不改）；
[DISALLOW] 禁止修改 knowledge/current/ 下任何文件；
[DISALLOW] 禁止执行 git commit（由后续 Committer 负责）；
[DISALLOW] 禁止跳过修正原因记录（amendmentReason 不允许为空）；
[MUST-DO] 归档包不存在（已被 /archive-knowledge 合并）时，必须告知用户并调整处理方式；
```

# 完成标准（Orchestrator 逐项验证）
```Text
--- scope=small 完成标准 ---
[ ] 归档包已定位且路径正确；
[ ] 设计文件已恢复到 .knowledge-work/<change>/（4 个文件）；
[ ] 用户已提供新 commit hash；
[ ] Archiver 子 Agent 已执行完整审计 + delta 重新生成（Amendment 覆盖模式）；
[ ] 新归档包中 delta.md 基于新 commit diff 生成（非旧版修补）；
[ ] work-stash.md 保留原有内容且追加「## 归档后修正」章节；
[ ] pipeline-state.json completedChanges 已更新 commit + amendment.status；
[ ] .knowledge-work/<change>/ 已清理；
[ ] progress.md 和 current-plan.md 已更新；

--- scope=medium/large 完成标准 ---
[ ] 归档包已定位且路径正确；
[ ] 设计文件已恢复到 .knowledge-work/<change>/（4 个文件）；
[ ] pipeline-state.json 已将变更移回流水线（currentStage 正确）；
[ ] amendment 字段已写入（scope/reason/originalCommit/startedAt）；
[ ] history 已追加 amendment_start 记录；
[ ] progress.md「当前状态」新增条目；
[ ] current-plan.md 新增条目；
[ ] 启动报告已输出且内容完整；
```
