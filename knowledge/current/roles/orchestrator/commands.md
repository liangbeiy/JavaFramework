# Pipeline Commands

用户通过 `/pipeline:*` 命令对 8 阶段流水线进行硬控制。所有 pipeline 命令操作 `.knowledge-work/pipeline-state.json` 作为阶段事实源。

| 命令 | 用途 | 参数 |
|------|------|------|
| `/pipeline:status` | 渲染状态面板 | 无 |
| `/pipeline:new` | 创建新变更并启动流水线 | `<name> [描述\|飞书链接]` |
| `/pipeline:next` | 推进到下一阶段 | `[--force]` |
| `/pipeline:goto` | 跳转到指定阶段 | `<stage> [--force]` |
| `/pipeline:approve` | CR 通过 | `[备注]` |
| `/pipeline:reject` | CR 打回 | `[fix\|redesign\|rethink] [原因]`（默认 fix） |
| `/pipeline:amend` | 归档后修正（团队 CR 打回） | `<change> [--scope]`（scope 自动推断+确认） |

## 阶段编号映射
`1=explore, 2=propose, 3=cr1, 4=build, 5=verify, 6=cr2, 7=commit, 8=archive`

## 门禁校验
Pipeline 命令在推进阶段时自动执行门禁校验。`--force` 可跳过校验，历史记录中标注 `forced:true`。
推进阶段时若需跳过中间阶段，必须先调用 `pipeline-guard stage-skip` 获得 PASS。

## 归档后修正（Amendment）
归档完成后收到团队 CR 反馈需要修改代码时，使用 `/pipeline:amend` 从归档包恢复设计文件并重新进入流水线。Amender 角色（`roles/amender.md`）负责协调恢复过程。完整协议见 `.opencode/skills/pipeline-amend/SKILL.md`。

**自动触发**：用户提到"团队 CR 打回"、"Gerrit 评论要改"等意图时，Orchestrator 自动识别并启动 amend 流程（无需显式 slash 命令）。

**Scope 自动推断**：未显式指定 `--scope` 时，Orchestrator 根据 CR 反馈内容推断 scope（small/medium/large），推断后必须向用户确认再执行。
