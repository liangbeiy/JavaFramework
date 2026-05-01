---
name: "pipeline-new"
description: "创建新变更并启动 8 阶段流水线。Invoke when user runs /pipeline:new, or asks to 创建变更、新建变更、启动流水线。"
---

# pipeline-new

创建新变更并启动 8 阶段流水线。

## 触发条件

用户输入 `/pipeline-new <name> [描述或飞书链接]` 时激活。

---

## 执行步骤

### 1. 解析输入

- `name`：第一个参数，kebab-case
- 描述/飞书链接：可选，传递给 Explorer
- `--force`：已有活跃变更时强制创建

未提供 name 时询问用户。

### 2. 冲突检查

读取 `.knowledge-work/pipeline-state.json`（如存在）。已有 `activeChange` 时：
- 无 `--force` → 警告并停止
- 有 `--force` → 继续，旧变更保留在 `changes` 中

### 3. 创建工作区

```bash
git branch --show-current  # 获取分支名
mkdir -p .knowledge-work/<name>/
```

### 4. 写入 pipeline-state.json

创建或更新 JSON，设置 `activeChange`、`currentStage=1`、`stageStatus="in_progress"`、空 gates、history 初始记录。

### 5. 更新 progress.md + current-plan.md

同步新变更的阶段信息。

### 6. 启动 Explorer 子 Agent

使用 task tool 发起子 Agent：
```
角色: Explorer
读取: knowledge/current/roles/explorer.md
变更: <name>
描述: <description>
产出: .knowledge-work/<name>/exploration.md
```

---

## JSON 操作规范

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）
- 写入时总是全量写回（2 空格缩进）
- history 只追加不删除
- 时间戳：ISO 8601 无时区（`2026-04-17T10:00:00`）
