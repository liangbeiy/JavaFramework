---
name: "openspec-explore"
description: "阶段 1 探索需求。作为思维伙伴帮用户理清需求，产出 exploration.md。Invoke when user wants to explore ideas, investigate problems, or clarify requirements."
---

# openspec-explore

阶段 1 — 探索需求。作为**思维伙伴**帮助用户理清需求，将模糊想法转化为结构化的 `exploration.md`。

支持三种输入模式：口头描述（纯文本）、PRD 文档（飞书 URL）、已有变更上下文（继续探索）。

> 探索模式是**思考时间，不是实现时间**。可以阅读文件、搜索代码、调研代码库，但绝不写代码或实现功能。

## 触发条件

Orchestrator 发起阶段 1 子 Agent 时自动加载。

---

## 探索姿态

```
- 好奇而非说教 — 提出自然涌现的问题，不按脚本走
- 开放式引导 — 展示多个可能方向，让用户选择感兴趣的
- 善用可视化 — 用 ASCII 图表辅助澄清（架构图、状态机、数据流、对比表）
- 跟随线索 — 发现有价值的岔路时跟进，不死板坚持原路
- 扎根代码 — 探索实际代码库，不空谈理论
- 不急于结论 — 让问题的形状自然浮现
```

---

## 执行协议

### 步骤 1：初始化上下文

```
1. 读取 AGENTS.md — 获取 Project Overview、Architecture、Architecture Constraints
2. 读取 knowledge/current/roles/explorer.md — 获取完整角色规范（本 Skill 的权威来源）
3. 确认变更名称 <change>
4. 识别输入模式：
   - 口头模式：用户提供纯文本需求描述
   - PRD 模式：用户提供飞书 PRD 文档 URL（一个或多个）
   - 继续模式：已存在 .knowledge-work/<change>/exploration.md，在其基础上深化
5. 创建 .knowledge-work/<change>/ 目录（如不存在）
```

### 步骤 1.5：PRD 文档获取（仅 PRD 模式）

```
1. 使用 feishu-mcp fetch-doc 获取 PRD 全文
2. 使用 feishu-mcp get-comments 获取 PRD 评论（可能包含需求变更和澄清）
3. 识别 PRD 中引用的关键飞书文档，递归获取（最多 2 层）
4. 从 PRD 中提取结构化信息：
   - 需求背景与目标
   - 功能点清单
   - UI/交互要求（有设计稿时记录 Figma 链接和页面名称）
   - 接口要求
   - 验收标准
   - 非功能需求
5. PRD 中的模糊描述转化为明确的开放问题
```

### 步骤 2：知识库导航

按需求涉及的模块读取知识库文档：

```
[必读] knowledge/current/constitution.md          — 技术栈约束
[必读] knowledge/current/glossary.md               — 统一术语
[必读] 目标模块的 modules/<Module>/overview.md      — 模块职责与边界
[按需] knowledge/current/architecture.md           — 模块依赖关系
[按需] knowledge/current/cross-cutting/ 下相关文档  — 横切关注点
```

### 步骤 3：代码路由定位

涉及代码定位时 **必须先查路由索引**，禁止盲目 Grep：

```
1. Grep "^ClassName\t" knowledge/current/router/symbol-table.tsv   — 定位类
2. Grep "^ClassName\tmethodName" knowledge/current/router/method-index.tsv — 定位方法
3. Grep "^ClassName\t" knowledge/current/router/call-graph.tsv     — 正向调用链
4. 定位后 Read 源码求证
```

### 步骤 4：历史快照回溯

涉及现有功能修改时（全新功能可跳过并说明理由）：

```
1. 读取 knowledge/snapshots/_index.md — 查看快照列表
2. 对比 snapshots/<版本>/modules/<Module>/ 与 current/ 同名文件
3. 提取历史决策线索，写入 exploration.md
```

### 步骤 5：源码求证

知识库信息不足时触发（触发条件见 roles/explorer.md）：

```
1. 先读 modules/<Module>/api.md 获取关键类名
2. Grep 定位源码文件
3. Read 目标代码段
4. 在 exploration.md 中标注来源（如「来源：XxxManager.java:120」）
```

### 步骤 5.5：可视化（按需）

探索过程中发现复杂结构时，用 ASCII 图表辅助澄清：

```
适用场景：
- 模块依赖关系 → 依赖图
- 数据流向 → 流程图
- 多方案对比 → 对比表
- 状态变化 → 状态机
- 调用链路 → 调用时序图

图表直接嵌入 exploration.md 相关章节中。
```

### 步骤 6：撰写 exploration.md

写入 `.knowledge-work/<change>/exploration.md`。

**PRD 模式** — 必须包含 6 个章节：

```markdown
# PRD 信息提取
- PRD 文档 URL：<url>
- 功能点清单：（从 PRD 提取的功能点，或标注「PRD 未提供」）
- UI/交互要求：（页面描述、设计稿链接等，或标注「PRD 未提供」）
- 接口要求：（API、数据格式等，或标注「PRD 未提供」）
- 验收标准：（AC 条目，或标注「PRD 未提供」）
- 非功能需求：（性能/兼容性/安全，或标注「PRD 未提供」）

# PRD 引用文档
（递归获取的关键引用文档列表及摘要，或标注「无关键引用文档」）

# 需求澄清
（融合 PRD 提取信息 + 知识库分析，明确功能边界）

# 依赖分析
（具体模块名，如 GameCacheLib；是否产生新的跨层依赖）

# 风险识别
（每条风险关联到具体架构约束编号，如「违反约束 #1：依赖方向」）

# 开放问题
（PRD 中的模糊描述转化的问题 + 需要用户确认的决策点）
```

**口头模式** — 必须包含 4 个章节：

```markdown
# 需求澄清
（功能边界：做什么 + 不做什么；非功能需求：性能/兼容性/安全）

# 依赖分析
（具体模块名，如 GameCacheLib；是否产生新的跨层依赖）

# 风险识别
（每条风险关联到具体架构约束编号，如「违反约束 #1：依赖方向」）

# 开放问题
（需要用户确认的决策点，不得自行假设）
```

### 步骤 7：返回结果

向 Orchestrator 返回：
1. **一句话摘要**（非空）
2. **是否有需要用户确认的开放问题**（是/否）
3. **输入模式**（口头/PRD/继续）

---

## 硬性约束

```
[ASSERT-DISALLOW] 禁止做方案设计或架构决策
[ASSERT-DISALLOW] 禁止修改任何源码文件
[ASSERT-DISALLOW] 禁止修改 knowledge/current/ 下的任何文档
[DISALLOW]        禁止读取 .knowledge-work/ 下其他变更的文件
[DISALLOW]        禁止通过 task_id 复用其他阶段的子 Agent 会话
```

---

## 完成标准

Orchestrator 逐项验证（完整清单见 `roles/explorer.md` 末尾），核心项：

- [ ] `exploration.md` 存在且非空
- [ ] 口头模式：4 个章节齐全（需求澄清/依赖分析/风险识别/开放问题）
- [ ] PRD 模式：6 个章节齐全（PRD 信息提取/PRD 引用文档/需求澄清/依赖分析/风险识别/开放问题）
- [ ] PRD 模式：PRD 信息提取章节记录了 PRD 原文 URL
- [ ] PRD 模式：PRD 中模糊描述已转化为开放问题
- [ ] 依赖分析指明具体模块名
- [ ] 风险关联到架构约束编号
- [ ] 不包含方案设计内容
- [ ] 涉及现有功能时引用了历史快照
- [ ] 源码引用标注了来源（文件名:行号）
- [ ] 返回了摘要、开放问题回答和输入模式
