import type { Plugin } from "@opencode-ai/plugin"

/**
 * Harness 工程门禁插件
 *
 * 12 类自动化门禁：
 * 1. 工作区留痕检查 — 每阶段完成后强制检查 progress.md / current-plan.md 已更新
 * 2. subAgent 切换 — 禁止 task tool 复用前序阶段 task_id
 * 3. code-style 强制读取 — Planner/Builder/Committer 阶段执行前必须先读取 style/ 目录
 * 4. 关键文件编辑拦截 — 用户声明的关键文件
 * 5. 依赖版本锁校验 — 用户声明的锁定依赖
 * 6. 会话启动 git 状态报告 + 工作区加载
 * 7. 提交前密钥扫描 — git commit 前扫描硬编码 API Key/Token/密码
 * 8. Scope 白名单拦截 — Builder 阶段 edit/write 时检查文件是否在声明范围内
 * 9. Builder 完成后自动构建验证
 * 10. 提交前代码风格校验
 * 11. Pipeline 阶段门禁 — 源码修改必须在 Builder 阶段（currentStage >= 4）
 * 12. 自动推进强制信号 — 子 Agent 返回后，非门禁阶段必须立即推进
 */

// ==================== 常量定义 ====================

// 源码文件扩展名正则 — 门禁 11 用于判断是否为源码文件
const SOURCE_CODE_PATTERN = /\.(java)$/

// 锁定依赖
const LOCKED_DEPENDENCIES: Record<string, { pattern: RegExp; required: string; constraint: string }> = {
    gson: {
        pattern: /["']com\.google\.code\.gson:gson["']\s*:\s*["'](.*?)["']/g,
        required: "2.13.1",
        constraint: "Gson 版本锁定 2.13.1",
    },
    junit: {
        pattern: /["']org\.junit\.jupiter["']\s*:\s*["'](.*?)["']/g,
        required: "5.10.0",
        constraint: "JUnit Jupiter 版本锁定 5.10.0",
    },
}

// 关键文件列表（编辑时会产生警告）
const CRITICAL_FILES: string[] = [
    "FrameworkContext.java",
    "EventBus.java",
    "LocalEventBus.java",
    "InterProcessEventBus.java",
    "DispatchQueue.java",
]

// 构建配置文件列表
const BUILD_CONFIG_FILES: string[] = [
    "build.gradle.kts",
    "settings.gradle.kts",
]

// 密钥扫描模式（通用）
const SECRET_PATTERNS = [
    /API[_-]?KEY\s*=\s*"[A-Za-z0-9+/=]{16,}"/,
    /TOKEN\s*=\s*"[A-Za-z0-9+/=_-]{16,}"/,
    /PASSWORD\s*=\s*"[^"]{8,}"/,
    /SECRET\s*=\s*"[^"]{8,}"/,
    /AKIA[0-9A-Z]{16}/,
    /BEGIN (RSA |EC |DSA )?PRIVATE KEY/,
    /Bearer [A-Za-z0-9+/=_-]{20,}/,
]

// 密钥扫描时的文件过滤正则
const SECRET_SCAN_FILE_PATTERN = /\.(java)$/

// code-style 目录下必须读取的文件
const CODE_STYLE_FILES = [
    "knowledge/current/style/code-style.md",
    "knowledge/current/style/default-code-style.editorconfig",
]

// 需要强制读取 code-style 的阶段关键词
const CODE_STYLE_REQUIRED_ROLES = ["planner", "builder", "committer"]

// ==================== 会话状态追踪 ====================

let lastProgressMtime = 0
let lastPlanMtime = 0
let codeStyleRead = false
let currentRole = ""

export const HarnessHooks: Plugin = async ({ $, directory }) => {
    try {
        const progressStat = await $`stat -f '%m' ${directory}/.knowledge-work/progress.md 2>/dev/null`.text()
        lastProgressMtime = parseInt(progressStat.trim()) || 0
        const planStat = await $`stat -f '%m' ${directory}/.knowledge-work/current-plan.md 2>/dev/null`.text()
        lastPlanMtime = parseInt(planStat.trim()) || 0
    } catch {
        // 文件不存在时忽略
    }

    return {
        "tool.execute.before": async (input, output) => {
            // ------ 门禁 11: Pipeline 阶段门禁（硬拦截）------
            if (input.tool === "edit" || input.tool === "write") {
                const filePath: string = output.args?.filePath || ""
                if (filePath) {
                    const isExempt =
                        filePath.includes(".knowledge-work/") ||
                        filePath.includes("knowledge/") ||
                        filePath.includes(".claude/") ||
                        filePath.includes(".opencode/") ||
                        filePath.endsWith("AGENTS.md") ||
                        filePath.endsWith("CLAUDE.md")
                    const isSourceCode = SOURCE_CODE_PATTERN.test(filePath)

                    if (!isExempt && isSourceCode) {
                        const pipelineStatePath = `${directory}/.knowledge-work/pipeline-state.json`
                        try {
                            const stateContent = await $`cat ${pipelineStatePath} 2>/dev/null`.text()
                            const state = JSON.parse(stateContent)
                            const changes: Array<{ name?: string; currentStage?: number }> = state.changes || []
                            const activeChange = changes[0]
                            if (!activeChange) {
                                throw new Error(
                                    `[Pipeline 门禁] ❌ 无活跃变更，禁止修改源码。\n` +
                                        `  请先使用 /pipeline:new <name> 创建变更。\n` +
                                        `  被拦截的文件: ${filePath.split("/").pop()}`
                                )
                            }
                            const currentStage = activeChange.currentStage || 0
                            if (currentStage < 4) {
                                const stageNames: Record<number, string> = {
                                    1: "探索", 2: "设计", 3: "CR₁",
                                    4: "实现", 5: "构建验证", 6: "CR₂",
                                    7: "提交", 8: "归档",
                                }
                                throw new Error(
                                    `[Pipeline 门禁] ❌ 当前阶段 ${currentStage}（${stageNames[currentStage] || "未知"}），不允许修改源码。\n` +
                                        `  源码修改仅在阶段 4（实现）及之后允许。\n` +
                                        `  变更: ${activeChange.name || "unknown"}\n` +
                                        `  被拦截的文件: ${filePath.split("/").pop()}\n\n` +
                                        `  如需推进到实现阶段: /pipeline:next\n` +
                                        `  如需强制跳转: /pipeline:goto build --force`
                                )
                            }
                        } catch (e) {
                            if (e instanceof Error && e.message.includes("Pipeline 门禁")) throw e
                            throw new Error(
                                `[Pipeline 门禁] ❌ 无活跃流水线，禁止修改源码。\n` +
                                    `  请先使用 /pipeline:new <name> 创建变更。\n` +
                                    `  被拦截的文件: ${filePath.split("/").pop()}`
                            )
                        }
                    }
                }
            }

            // ------ 门禁 2: subAgent 切换检查 ------
            if (input.tool === "task") {
                const taskId = output.args?.task_id
                if (taskId) {
                    const prompt: string = output.args?.prompt || ""
                    const promptLower = prompt.toLowerCase()
                    const newRole = CODE_STYLE_REQUIRED_ROLES.find((r) => promptLower.includes(r)) || ""
                    if (newRole && currentRole && newRole !== currentRole) {
                        throw new Error(
                            `[Harness 门禁] ❌ 禁止跨角色复用 subAgent 会话！\n` +
                                `  当前角色: ${currentRole}, 新角色: ${newRole}\n` +
                                `  每个阶段必须启动全新子 Agent。`
                        )
                    }
                }
                const prompt: string = output.args?.prompt || ""
                const promptLower = prompt.toLowerCase()
                const detectedRole = CODE_STYLE_REQUIRED_ROLES.find((r) => promptLower.includes(r))
                if (detectedRole) {
                    currentRole = detectedRole
                    codeStyleRead = false
                }
            }

            // ------ 门禁 3: code-style 强制读取 ------
            if (
                (input.tool === "edit" || input.tool === "write") &&
                CODE_STYLE_REQUIRED_ROLES.includes(currentRole) &&
                !codeStyleRead
            ) {
                const filePath: string = output.args?.filePath || ""
                if (filePath && !filePath.includes(".knowledge-work/") && !filePath.includes("knowledge/")) {
                    console.error(
                        `[Harness 门禁] ⚠️ ${currentRole} 阶段尚未读取代码风格规范！\n` +
                            `  必须先读取: knowledge/current/style/code-style.md`
                    )
                }
            }

            if (input.tool === "read") {
                const filePath: string = output.args?.filePath || ""
                if (CODE_STYLE_FILES.some((f) => filePath.includes(f))) {
                    codeStyleRead = true
                }
            }

            // ------ 门禁 8: Scope 白名单拦截 ------
            if (
                (input.tool === "edit" || input.tool === "write") &&
                currentRole === "builder"
            ) {
                const filePath: string = output.args?.filePath || ""
                if (filePath && !filePath.includes(".knowledge-work/") && !filePath.includes("knowledge/")) {
                    try {
                        const whitelistFiles = (
                            await $`find ${directory}/.knowledge-work -name 'scope_whitelist.txt' -maxdepth 2 2>/dev/null`.text()
                        ).trim()
                        if (whitelistFiles) {
                            const whitelistPath = whitelistFiles.split("\n")[0]
                            const whitelist = (await $`cat ${whitelistPath}`.text()).trim()
                            if (whitelist) {
                                const patterns = whitelist.split("\n").filter((l: string) => l.trim())
                                const relativePath = filePath.replace(directory + "/", "")
                                const inScope = patterns.some((pattern: string) => {
                                    if (pattern.includes("**")) {
                                        const prefix = pattern.replace(/\*\*.*/, "")
                                        return relativePath.startsWith(prefix)
                                    }
                                    return relativePath === pattern.trim() || relativePath.endsWith(pattern.trim())
                                })
                                if (!inScope) {
                                    console.warn(
                                        `[Harness 门禁] ⚠️ Scope 越界警告！\n` +
                                            `  文件: ${relativePath}\n` +
                                            `  该文件不在 scope_whitelist.txt 声明范围内。`
                                    )
                                }
                            }
                        }
                    } catch {
                        // scope 检查失败不阻断
                    }
                }
            }

            // ------ 门禁 7: 提交前密钥扫描 ------
            if (input.tool === "bash") {
                const command: string = output.args?.command || ""
                if (/git\s+commit/.test(command)) {
                    try {
                        const stagedFiles = (
                            await $`git -C ${directory} diff --cached --name-only --diff-filter=ACM 2>/dev/null`.text()
                        ).trim()
                        const sourceFiles = stagedFiles
                            .split("\n")
                            .filter((f) => SECRET_SCAN_FILE_PATTERN.test(f) && !/\/test\//.test(f) && !/\/__tests__\//.test(f))
                        for (const file of sourceFiles) {
                            const fullPath = `${directory}/${file}`
                            try {
                                const content = await $`cat ${fullPath}`.text()
                                const lines = content.split("\n")
                                for (let i = 0; i < lines.length; i++) {
                                    if (lines[i].includes("harness:ignore-secret")) continue
                                    for (const pattern of SECRET_PATTERNS) {
                                        if (pattern.test(lines[i])) {
                                            throw new Error(
                                                `[Harness 门禁] ❌ 检测到疑似硬编码密钥/Token！\n` +
                                                    `  文件: ${file}:${i + 1}\n` +
                                                    `  内容: ${lines[i].trim().substring(0, 80)}\n` +
                                                    `  如确认为误报，添加注释: // harness:ignore-secret`
                                            )
                                        }
                                    }
                                }
                            } catch (e) {
                                if (e instanceof Error && e.message.includes("Harness 门禁")) throw e
                            }
                        }
                    } catch (e) {
                        if (e instanceof Error && e.message.includes("Harness 门禁")) throw e
                    }
                }
            }

            // ------ 门禁 10: 提交前代码风格校验 ------
            if (input.tool === "bash") {
                const command2: string = output.args?.command || ""
                if (/git\s+commit/.test(command2)) {
                    try {
                        const styleResult = await $`${directory}/.opencode/scripts/harness/verify-code-style.sh 2>&1`.text()
                        if (styleResult.includes("STYLE_CHECK_RESULT=FAIL")) {
                            throw new Error(
                                `[Harness 门禁] ❌ 代码风格校验失败！\n${styleResult}\n` +
                                    `  请修复风格违规后重新提交。`
                            )
                        }
                    } catch (e) {
                        if (e instanceof Error && e.message.includes("Harness 门禁")) throw e
                    }
                }
            }

            // ------ 门禁 4: 关键文件编辑拦截 ------
            if (input.tool !== "edit" && input.tool !== "write") return

            const filePath: string = output.args?.filePath || ""
            if (!filePath) return
            const fileName = filePath.split("/").pop() || ""

            if (CRITICAL_FILES.some((f) => fileName === f)) {
                console.warn(
                    `[Harness 门禁] ⚠️ 正在编辑关键文件: ${fileName}\n` +
                        `  请确认本次修改不会破坏核心功能。`
                )
            }

            if (BUILD_CONFIG_FILES.some((f) => fileName === f || filePath.endsWith(`/${f}`))) {
                console.warn(
                    `[Harness 门禁] ⚠️ 正在编辑构建配置文件: ${fileName}\n` +
                        `  请确认不会破坏依赖版本约束。`
                )
            }
        },

        "tool.execute.after": async (input, output) => {
            // ------ 门禁 12: 自动推进强制信号 ------
            if (input.tool === "task") {
                try {
                    const pipelineStatePath = `${directory}/.knowledge-work/pipeline-state.json`
                    const stateContent = await $`cat ${pipelineStatePath} 2>/dev/null`.text()
                    const state = JSON.parse(stateContent)
                    const changes: Array<{ name?: string; currentStage?: number; stageStatus?: string }> = state.changes || []
                    const activeChange = changes[0]
                    if (activeChange) {
                        const stage = activeChange.currentStage || 0
                        const humanGateStages = [3, 6]
                        const nextRoleMap: Record<number, string> = {
                            1: "planner",
                            2: "user_cr1",
                            4: "verifier",
                            5: "user_cr2",
                            7: "archiver",
                        }
                        const nextRole = nextRoleMap[stage]
                        if (nextRole && !humanGateStages.includes(stage)) {
                            console.error(
                                `\n[Harness 门禁 12] ⚡ 自动推进指令 ⚡\n` +
                                    `  ┌─────────────────────────────────────────────────────┐\n` +
                                    `  │ NEXT_ACTION: 立即发起 ${nextRole} 子 Agent              │\n` +
                                    `  │ 当前阶段: ${stage} → 下一阶段: ${stage + 1}                      │\n` +
                                    `  │ 变更: ${activeChange.name}                              │\n` +
                                    `  │ [ASSERT-DISALLOW] 禁止向用户确认！                   │\n` +
                                    `  └─────────────────────────────────────────────────────┘\n`
                            )
                        } else if (humanGateStages.includes(stage)) {
                            console.log(
                                `[Harness 门禁 12] 🚪 人工门禁阶段 ${stage}，等待用户审查结论。`
                            )
                        }
                    }
                } catch {
                    console.warn(`[Harness 门禁 12] ⚠️ 无法读取 pipeline-state.json`)
                }
            }

            // ------ 门禁 9: Builder 完成后自动构建验证 ------
            if (input.tool === "task" && currentRole === "builder") {
                try {
                    console.log(`[Harness] 🔨 Builder 子 Agent 返回，自动触发构建验证...`)
                    const buildResult = await $`${directory}/.opencode/scripts/harness/verify-build.sh debug 2>&1`.text()
                    if (buildResult.includes("BUILD_RESULT=PASS")) {
                        console.log(`[Harness] ✅ 构建验证通过`)
                    } else {
                        console.error(
                            `[Harness] ❌ 构建验证失败！\n` +
                                `  错误摘要:\n${buildResult.split("\n").slice(-20).join("\n")}`
                        )
                    }
                } catch {
                    console.warn(`[Harness] ⚠️ 自动构建验证执行异常`)
                }
            }

            // ------ 门禁 1: 工作区留痕检查 ------
            if (input.tool === "task") {
                try {
                    const progressStat =
                        await $`stat -f '%m' ${directory}/.knowledge-work/progress.md 2>/dev/null`.text()
                    const currentProgressMtime = parseInt(progressStat.trim()) || 0
                    const planStat =
                        await $`stat -f '%m' ${directory}/.knowledge-work/current-plan.md 2>/dev/null`.text()
                    const currentPlanMtime = parseInt(planStat.trim()) || 0

                    if (currentProgressMtime <= lastProgressMtime) {
                        console.error(
                            `[Harness 门禁] ❌ .knowledge-work/progress.md 未更新！`
                        )
                    }
                    if (currentPlanMtime <= lastPlanMtime) {
                        console.error(
                            `[Harness 门禁] ❌ .knowledge-work/current-plan.md 未更新！`
                        )
                    }
                    lastProgressMtime = currentProgressMtime
                    lastPlanMtime = currentPlanMtime
                } catch {
                    console.warn("[Harness 门禁] ⚠️ 无法检查工作区文件状态")
                }
            }

            // ------ 门禁 5: 依赖版本锁校验 ------
            if (input.tool !== "edit" && input.tool !== "write") return
            const filePath: string = input.metadata?.filePath || output.args?.filePath || ""
            if (!filePath) return
            const fileName = filePath.split("/").pop() || ""

            if (BUILD_CONFIG_FILES.some((f) => fileName === f)) {
                try {
                    const content = await $`cat ${filePath}`.text()
                    for (const [name, dep] of Object.entries(LOCKED_DEPENDENCIES)) {
                        const matches = [...content.matchAll(dep.pattern)]
                        for (const match of matches) {
                            const version = match[1]
                            if (version !== dep.required) {
                                console.error(
                                    `[Harness 门禁] ❌ 依赖版本违规: ${name} ${version}, 要求 ${dep.required}\n` +
                                        `  ${dep.constraint}`
                                )
                            }
                        }
                    }
                } catch {
                    // 文件读取失败不阻断
                }
            }
        },

        event: async ({ event }) => {
            if (event.type === "session.created") {
                try {
                    const branch = (await $`git -C ${directory} branch --show-current`.text()).trim()
                    const lastCommit = (
                        await $`git -C ${directory} log -1 --format='%h %s (%cr)' 2>/dev/null`.text()
                    ).trim()
                    const uncommitted = (
                        await $`git -C ${directory} status --porcelain 2>/dev/null`.text()
                    ).trim()
                    const uncommittedCount = uncommitted ? uncommitted.split("\n").length : 0
                    console.log(
                        `[Harness] Git: ${branch} | ${lastCommit} | ${uncommittedCount} uncommitted`
                    )
                } catch {}
                try {
                    const pipelineStatePath = `${directory}/.knowledge-work/pipeline-state.json`
                    const pipelineExists =
                        await $`test -f ${pipelineStatePath} && echo "yes" || echo "no"`.text()
                    if (pipelineExists.trim() === "yes") {
                        const stateContent = await $`cat ${pipelineStatePath}`.text()
                        const state = JSON.parse(stateContent)
                        const changes: Array<{ name?: string; currentStage?: number; stageStatus?: string }> = state.changes || []
                        const activeChange = changes[0]
                        if (activeChange) {
                            const stageNames: Record<number, string> = {
                                1: "探索", 2: "设计", 3: "CR₁", 4: "实现",
                                5: "构建验证", 6: "CR₂", 7: "提交", 8: "归档",
                            }
                            console.log(
                                `[Harness] Pipeline: ${activeChange.name} @ 阶段 ${activeChange.currentStage}（${stageNames[activeChange.currentStage || 0] || "?"}）`
                            )
                        } else {
                            console.log(`[Harness] Pipeline: idle`)
                        }
                    }
                } catch {}
            }
        },
    }
}
