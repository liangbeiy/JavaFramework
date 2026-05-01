---
name: "bugfix-analysis"
description: "Bug 根因分析 + 修复方案自动闭环。Invoke when user asks to analyze a bug, 分析 bug, or provides crash/ANR logs for diagnosis."
---

# bugfix-analysis

Bug 根因分析 + 修复方案自动闭环。

支持两种输入模式：
- **Jira 驱动**：从 Jira Issue 自动获取信息、下载日志、分析根因、产出飞书报告、回写 Jira
- **手动驱动**：用户直接提供 Bug 描述/日志/堆栈，分析根因并产出方案

## 触发条件

用户报告 Bug 或要求分析问题时使用。典型指令：
- "分析这个 Jira：BUGOS2-741839"
- "帮我看一下 https://jira-phone.mioffice.cn/browse/BUGOS2-741839"
- "分析一下这个崩溃"（附堆栈）
- "这个 ANR 是什么原因"（附 bugreport）

---

## MCP 依赖

| MCP | 用途 | 必需性 |
|-----|------|--------|
| **mi-jira** | 获取 Issue 详情 / 添加评论 / 更新状态 | Jira 模式必需 |
| **feishu-mcp** | 创建分析报告飞书文档 | 推荐 |
| **Playwright** | 自动下载小米网盘日志附件 | 日志下载时必需 |

### mi-jira MCP 工具清单

```
mcp_mi-jira_jira_get_issue        — 获取 Issue 详情（标题/描述/附件/状态）
mcp_mi-jira_jira_add_comment      — 添加分析结论评论
mcp_mi-jira_jira_update_issue     — 更新 Issue 字段
mcp_mi-jira_jira_transition_issue — 变更 Issue 状态（如 开发中→已修复）
```

---

## 执行协议

### Phase 1：信息获取

#### 1A — Jira 模式（输入为 Jira 链接或 Issue Key）

```
1. 解析 Issue Key（如 BUGOS2-741839）
2. 调用 mi-jira MCP 获取 Issue 详情：
   - 标题、描述、优先级、影响版本
   - 附件列表（日志/截图/录屏）
   - 复现步骤、影响范围
3. 从描述中提取小米网盘链接和提取码
4. 如有网盘链接 → 使用 jira-issue-resolver/download_pan.py 下载，再委托 bugreport-log-analyzer 分析
5. 如有截图附件 → 下载截图辅助分析
```

#### 1B — 手动模式（输入为 Bug 描述/日志/堆栈）

```
1. 解析 Bug 描述，提取关键线索：
   - 类名 / 方法名 / 异常类型 / 错误码
   - 包名 com.xiaomi.minigame
2. 如有堆栈，提取调用链中的项目代码帧（过滤 Android framework 帧）
3. 确认影响版本和复现条件
```

### Phase 2：知识库查询

```
1. 搜索 knowledge/current/ 中是否有相关的「⚠️ 已知问题」记录
   — 可能是已知但未修复的问题，直接复用历史分析
2. 读取涉及模块的 overview.md / business.md 理解业务流程
3. 读取相关 cross-cutting/ 文档（IPC/多进程/下载/Bridge 等）
4. 检查 knowledge/snapshots/ 确认是否为回归问题
   — 对比历史快照：该功能在之前版本是否正常？
```

### Phase 3：Bugreport 日志分析（如有日志文件）

委托 `bugreport-log-analyzer` Skill 执行：

```
1. 双层解压（外层 zip → 内层 zip → 核心 txt）
2. 关键词搜索（按问题类型选择搜索策略）：
   - Crash/ANR：FATAL EXCEPTION / AndroidRuntime / ANR in / CRASH
   - 功能问题：搜索 com.xiaomi.minigame 包名相关日志
   - 性能问题：搜索耗时操作、卡顿、内存相关信息
   - 通用：Exception / Error / APP_SCOUT
3. 提取关键日志片段，关联到具体类/方法
```

### Phase 4：代码路由定位

**必须先查路由索引，禁止盲目 Grep：**

```
1. Grep "^ClassName\t" knowledge/current/router/symbol-table.tsv  — 定位类
2. Grep "^ClassName\t" knowledge/current/router/call-graph.tsv    — 正向调用链
3. Grep "\tClassName\t" knowledge/current/router/call-graph.tsv   — 反向调用链（谁调用了它）
4. Grep "^ClassName\tmethodName" knowledge/current/router/method-index.tsv — 定位方法
5. Read 源码分析实现逻辑
```

### Phase 5：根因分析

```
1. 沿堆栈/日志线索从上到下分析每一帧的代码逻辑
2. 识别异常触发条件（什么输入/状态导致了此路径）
3. 确认问题分类：
   - 代码 Bug（逻辑错误/空指针/越界/竞态等）
   - 配置问题（云配置/本地配置/权限等）
   - 环境问题（设备/系统版本/运行时状态等）
4. 标注根因确定性等级：
   - 确定 — 有源码级证据（文件名:行号 + 复现路径）
   - 高度可能 — 日志 + 代码逻辑推断一致
   - 需要更多信息 — 信息不足，列出还需要什么
5. 涉及多进程问题时，必须分析所有相关进程（主进程 + :Launcher0-4）
```

### Phase 6：修复方案产出

产出结构化修复方案：

```markdown
# Bug 分析报告 — <Bug 标题>

## 基本信息
- Jira Issue: <Issue Key>（如适用）
- 优先级: <P0/P1/P2/P3>
- 影响版本: <版本号>
- 影响模块: <模块名>

## 现象
（Bug 现象描述，含复现步骤）

## 日志关键片段
（从 bugreport 中提取的关键日志，标注文件和行号）

## 根因
（根因分析，标注确定性等级）
- 确定性: <确定/高度可能/需要更多信息>
- 来源: <XxxManager.java:120>
- 触发条件: <具体描述>

## 影响范围
（哪些模块/进程/场景受影响）

## 修复方案
### 方案 A（推荐）
- 修改文件: <文件列表>
- 修改内容: <具体描述>
- 预估改动量: <行数>
- 风险: <兼容性/回归风险评估>

### 方案 B（备选）
- ...

## 回归验证
（修复后需要验证的场景列表，GIVEN/WHEN/THEN 格式）

## 关联知识
（应沉淀到知识库 ⚠️ 已知问题的经验）
```

### Phase 7：报告产出与 Jira 回写（Jira 模式）

```
1. 调用 feishu-mcp 创建飞书文档（使用上述报告格式）
2. 调用 mi-jira MCP 添加评论：
   - 摘要：根因 + 推荐方案 + 飞书报告链接
3. 如已有修复代码 → 更新 Issue 状态
```

---

## 与 Harness 流水线的集成

BugFix 场景进入标准 8 阶段流水线时的特殊处理：

| 阶段 | BugFix 特殊要求 |
|------|----------------|
| 阶段 1 Explorer | 输入可以是本 Skill 的分析报告（替代用户原始需求） |
| 阶段 2 Planner | spec.md 中 **必须包含回归场景**（MODIFIED Requirements + Regression 标记） |
| 阶段 4 Builder | **禁止顺手重构**——只修改根因相关代码 |
| 阶段 8 Archiver | 将分析报告中的「关联知识」写入 ⚠️ 已知问题章节 |

---

## 约束

```
[MUST-DO] 根因必须有源码级证据支撑（文件名:行号）
[MUST-DO] 修复方案必须评估回归风险
[MUST-DO] 涉及多进程问题时必须分析所有相关进程（主进程 + :Launcher0-4）
[MUST-DO] Jira 模式下必须产出飞书报告
[MUST-DO] 日志文件下载后必须解压并分析，不能只下载不分析
[DISALLOW] 根因不确定时禁止推测性修复，应列为「需要更多信息」
[DISALLOW] 禁止在分析阶段修改代码（分析和修复分离）
[DISALLOW] 处理日志时注意脱敏（手机号/IMEI/账号等敏感信息）
```
