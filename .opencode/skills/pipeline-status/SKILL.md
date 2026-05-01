---
name: "pipeline-status"
description: "显示当前流水线状态面板 — 阶段进度、门禁状态、下一步建议。Invoke when user runs /pipeline:status, or asks to 流水线状态、当前进度。"
---

# pipeline-status

显示当前流水线状态面板 — 阶段进度、门禁状态、下一步建议。

## 触发条件

用户输入 `/pipeline-status` 或询问流水线状态时激活。

---

## 执行步骤

### 1. 读取流水线状态

读取 `.knowledge-work/pipeline-state.json`。

**如果文件不存在**，输出后停止：
```
Pipeline 空闲，无活跃变更。
使用 /pipeline-new <name> [描述] 创建新变更。
```

### 2. 解析并渲染状态面板

从 JSON 中提取 `activeChange` 对应变更的状态，渲染：

```
## 流水线状态

**变更**: {activeChange} — {description}
**分支**: {branch}

  ①探索 → ②设计 → ③CR₁ → ④实现 → ⑤构建 → ⑥CR₂ → ⑦提交 → ⑧归档
  {✓=已完成, ▸=进行中, ○=未开始}

### 门禁
| 门禁 | 状态 | 时间 | 备注 |
|------|------|------|------|
| CR₁  | {status} | {at} | {note} |
| 构建  | {status} | {at} | |
| CR₂  | {status} | {at} | {note} |

### 下一步
{根据当前阶段给出建议}
```

**阶段编号映射**：`1=探索 2=设计 3=CR₁ 4=实现 5=构建验证 6=CR₂ 7=提交 8=归档`

---

## JSON 格式

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）

## 约束

- **只读命令**：绝不修改任何文件。
