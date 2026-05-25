# Coroutine 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.coroutine |
| 业务域 | 协程调度 / 异步任务 |
| 源文件数 | 3 + queue/task/exception 子模块 |

## 模块定位

Coroutine 模块是 JavaFramework 的异步调度基础设施，提供类似协程的轻量级任务调度能力。核心 `DispatchQueue` 接口定义了 sync/async/remove/shutdown 等调度契约，实现类 `DispatcherQueue` 基于优先级队列和线程池提供两种执行模式：标准模式（单线程顺序执行）和协程模式（线程池并发执行）。

`Coroutines` 类统一管理全局调度器实例（standard/io/once），并提供自定义协程队列的注册/获取能力。

`DispatchGroup` 提供任务组编排能力，允许批量提交异步任务并在所有任务完成后触发回调通知，基于 Semaphore 实现任务完成等待。模块还提供 `IDLETask` 接口支持空闲任务复用，以及完善的观察者机制监控调度器状态变化。

## 直接依赖

```
com.cxuy.framework.coroutine
├── com.cxuy.framework.annotation
│   ├── @NonNull
│   └── @Nullable
├── com.cxuy.framework.coroutine.queue
│   ├── DispatcherQueue（实现类）
│   └── OnceDispatcherQueue（单次执行队列，@Deprecated）
├── com.cxuy.framework.coroutine.task
│   ├── Task（任务接口）
│   ├── TaskItem（任务项）
│   └── IDLETask（空闲任务接口）
└── com.cxuy.framework.coroutine.exception
    ├── DispatcherQueueHasDestroyedException
    ├── NeedDestroyDispatcherQueueException
    ├── GroupHasCommittedException
    ├── GroupHasDoneException
    └── GroupStatusErrorException
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `DispatchQueue` | 调度队列顶层接口，定义 sync/async/remove/shutdown 等契约 |
| `Coroutines` | 调度器实例管理器，提供 standard/io 全局实例 + 自定义队列管理 |
| `DispatchGroup` | 任务组，支持批量提交+完成通知，基于 Semaphore 等待 |
| `DispatcherQueue` | DispatchQueue 标准实现，基于优先级队列+线程 |
| `OnceDispatcherQueue` | 单次执行队列，任务执行后自动销毁（@Deprecated since 1.31.5.0） |
| `Task` | 任务接口（实质为 Runnable），代表可执行单元 |
| `TaskItem` | 任务包装项，含延迟时间和优先级信息 |
| `TaskItemManager` | TaskItem 对象池管理器，复用任务项减少 GC |
| `IDLETask` | 空闲任务接口，支持 reuse() 复用 |
| `Status` | 调度器状态枚举：INIT/BUSY/IDLE/DORMANT/DESTROY |
| `StatusObserver` | 状态观察者接口，监听调度器状态变化 |
