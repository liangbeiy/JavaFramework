# Commit Convention

## Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

## Types

| Type | Description |
|------|-------------|
| feat | 新功能 |
| fix | Bug 修复 |
| docs | 文档变更 |
| style | 代码格式（不影响功能） |
| refactor | 重构（不是新功能也不是修复） |
| perf | 性能优化 |
| test | 测试相关 |
| chore | 构建/工具链变更 |

## Scopes

| Scope | Description |
|-------|-------------|
| framework | 核心框架 |
| eventbus | 事件总线 |
| coroutine | 协程调度 |
| io | IO 操作 |
| http | HTTP 客户端 |
| server | 服务器组件 |
| handler | 请求处理器 |
| deps | 依赖更新 |

## Examples

```
feat(eventbus): add InterProcessEventBus support

Implement cross-process event bus with server/client mode.
Supports register, unregister, post, and heartbeat operations.

Closes #123
```

```
fix(coroutine): handle DispatcherQueue destroy race condition

Added state check before dispatching tasks to avoid NPE.
```
