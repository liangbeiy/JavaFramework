---
name: "pipeline-goto"
description: "跳转到指定流水线阶段。前进执行门禁，后退无需校验。Invoke when user runs /pipeline:goto, or asks to 跳转阶段、回退到某阶段。"
---

# pipeline-goto

跳转到指定流水线阶段。前进时执行门禁校验，后退时无需校验。

## 触发条件

用户输入 `/pipeline-goto <stage> [--force]` 时激活。

---

## 执行步骤

### 1. 解析目标阶段

数字 `1-8` 或名称：`explore=1, propose=2, cr1=3, build=4, verify=5, cr2=6, commit=7, archive=8`

### 2. 读取 pipeline-state.json

文件不存在 → 报错停止。

### 3. 判断跳转方向

- `target > current` → 前进，需门禁校验
- `target < current` → 后退，无需校验
- `target === current` → "已在该阶段"，停止

### 4. 前进门禁校验

仅校验目标阶段的门禁（同 next 的校验规则表）。`--force` 跳过。

### 5. 更新 JSON

history 追加 `{ stage, action: "goto", from, at }` ，`--force` 时加 `forced: true`。

### 6. 更新 markdown

### 7. CR 阶段提示

→ 3 或 6 → 不启动子 Agent，提示等待 approve/reject。

### 8. 启动子 Agent

同 next 的角色调度表。后退时 prompt 说明"从阶段 {from} 回退到阶段 {target} 的重做"。

---

## JSON 操作规范

- **嵌套格式**：`activeChange` 为字符串 key，变更数据在 `changes[activeChange]` 对象中
- 变更对象字段：`stageStatus`（非 `status`），history 时间戳字段为 `at`（非 `timestamp`）
- 全量写回，history 只追加，ISO 8601 时间戳
