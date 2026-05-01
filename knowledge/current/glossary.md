# Glossary

## Terms

| Term | Definition | Source |
|------|------------|--------|
| EventBus | 事件总线，支持进程内和跨进程事件分发 | framework/eventbus |
| LocalEventBus | 进程内事件总线实现 | framework/eventbus/LocalEventBus.java |
| InterProcessEventBus | 跨进程事件总线，支持服务端/客户端模式 | framework/eventbus/InterProcessEventBus.java |
| Subscribe | 注解，标记事件订阅方法 | framework/eventbus/annotate/Subscribe.java |
| DispatchQueue | 协程调度队列抽象 | framework/coroutine/DispatchQueue.java |
| DispatcherQueue | 调度器队列，支持一次性和循环调度 | framework/coroutine/queue/DispatcherQueue.java |
| Task | 协程任务抽象 | framework/coroutine/task/Task.java |
| DispatchGroup | 任务组，支持多个任务完成后回调 | framework/coroutine/DispatchGroup.java |
| KVMap | Key-Value 内存映射接口 | framework/io/kv/KVMap.java |
| KVProvider | KV 存储提供者接口 | framework/io/kv/KVProvider.java |
| SimpleKV | 简单 KV 存储实现 | framework/io/kv/SimpleKV.java |
| FileProvider | 文件操作提供者接口 | framework/io/file/FileProvider.java |
| FileManager | 文件管理器，统一文件操作入口 | framework/io/file/FileManager.java |
| FrameworkContext | 框架全局上下文，单例 | framework/context/FrameworkContext.java |
| Context | 通用上下文接口 | framework/context/Context.java |
| BuildConfig | 编译期配置，包含版本等信息 | framework/context/BuildConfig.java |
| Gson | Google JSON 库，用于序列化/反序列化 | dependencies |
| Message | 跨进程通信消息基类 | framework/eventbus/cross/protocol/Message.java |
| Operation | 跨进程操作类型枚举 | framework/eventbus/cross/protocol/Operation.java |
| PostData | 事件发布数据载体 | framework/eventbus/cross/protocol/data/PostData.java |
| DecenterManager | 去中心化管理器，用于跨进程节点管理 | framework/eventbus/cross/decenter/DecenterManager.java |
| Role | 跨进程节点角色（SERVER/CLIENT） | framework/eventbus/cross/decenter/Role.java |
