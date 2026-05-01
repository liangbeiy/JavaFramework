import { tool } from "@opencode-ai/plugin"
import path from "path"

/**
 * Pipeline 状态检查工具
 *
 * 读取 pipeline-state.json，返回当前状态和下一步应执行的动作。
 * Orchestrator 在子 Agent 返回后必须调用此工具，根据输出决定下一步。
 *
 * 返回格式：
 * - NEXT_ACTION: agent/<role> → 立即发起对应子 Agent（禁止确认）
 * - NEXT_ACTION: human_gate → 向用户请求审查
 * - NEXT_ACTION: none → 流水线已完成或无活跃变更
 */
export default tool({
    description:
        "读取 pipeline-state.json 返回流水线当前状态和下一步强制动作。" +
        "子 Agent 返回后必须调用。返回 NEXT_ACTION 指令，Orchestrator 必须立即执行，禁止向用户确认。",
    args: {},
    async execute(args, context) {
        const pipelineStatePath = path.join(context.worktree, ".knowledge-work/pipeline-state.json")
        try {
            const fs = await import("fs")
            const stateContent = fs.readFileSync(pipelineStatePath, "utf-8")
            const state = JSON.parse(stateContent)
            const changes: Array<{
                name?: string
                currentStage?: number
                stageStatus?: string
            }> = state.changes || []
            const activeChange = changes[0]

            if (!activeChange) {
                return "PIPELINE_STATUS: idle\nNEXT_ACTION: none\n无活跃变更。"
            }

            const stage = activeChange.currentStage || 0
            const status = activeChange.stageStatus || "unknown"
            const name = activeChange.name || "unknown"

            const stageNames: Record<number, string> = {
                1: "探索",
                2: "设计",
                3: "CR₁",
                4: "实现",
                5: "构建验证",
                6: "CR₂",
                7: "提交",
                8: "归档",
            }

            // 阶段完成后→下一个角色
            const stageToNextRole: Record<number, { role: string; stage: number }> = {
                1: { role: "planner", stage: 2 },
                2: { role: "human_gate", stage: 3 },
                4: { role: "verifier", stage: 5 },
                5: { role: "human_gate", stage: 6 },
                7: { role: "archiver", stage: 8 },
            }

            // 当前阶段内→执行角色
            const stageToCurrentRole: Record<number, string> = {
                1: "explorer",
                2: "planner",
                4: "builder",
                5: "verifier",
                7: "committer",
                8: "archiver",
            }

            const humanGateStages = [3, 6]
            const isHumanGate = humanGateStages.includes(stage)

            let nextAction: string
            let instruction: string

            if (isHumanGate) {
                nextAction = "human_gate"
                instruction = `向用户请求 CR 审查结论（approved / 打回）。`
            } else if (stageToNextRole[stage]) {
                const next = stageToNextRole[stage]
                if (next.role === "human_gate") {
                    nextAction = "human_gate"
                    instruction = `当前阶段 ${stage}(${stageNames[stage]}) 完成后进入人工门禁 ${next.stage}(${stageNames[next.stage]})，向用户请求审查。`
                } else {
                    nextAction = `agent/${next.role}`
                    instruction =
                        `[ASSERT-DISALLOW] 立即发起 ${next.role} 子 Agent 进入阶段 ${next.stage}(${stageNames[next.stage]})。\n` +
                        `禁止向用户确认。禁止输出 "需要继续吗？" / "是否推进？" / "要我开始吗？" 等确认语。`
                }
            } else if (stageToCurrentRole[stage]) {
                nextAction = `agent/${stageToCurrentRole[stage]}`
                instruction =
                    `当前阶段 ${stage}(${stageNames[stage]}) 进行中，需发起 ${stageToCurrentRole[stage]} 子 Agent 执行。\n` +
                    `[ASSERT-DISALLOW] 禁止向用户确认，直接执行。`
            } else if (stage === 8 && status === "completed") {
                nextAction = "none"
                instruction = "流水线已完成归档。"
            } else {
                nextAction = "none"
                instruction = `阶段 ${stage} 状态 ${status}，无明确下一步。`
            }

            return [
                `PIPELINE_STATUS: active`,
                `CHANGE: ${name}`,
                `STAGE: ${stage} (${stageNames[stage] || "unknown"})`,
                `STATUS: ${status}`,
                `NEXT_ACTION: ${nextAction}`,
                `INSTRUCTION: ${instruction}`,
            ].join("\n")
        } catch (e) {
            return `PIPELINE_STATUS: error\nNEXT_ACTION: none\n无法读取 pipeline-state.json: ${e}`
        }
    },
})
