# EventBus 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.eventbus |
| 业务域 | 事件驱动 / 消息分发 |
| 源文件数 | 3 + cross 子模块 |

## 模块定位

EventBus 是 JavaFramework 的核心事件驱动基础设施，提供进程内和跨进程两种事件分发能力。本地事件总线基于注解驱动的订阅者模式，通过 `@Subscribe` 注解自动发现事件处理方法，利用 `DispatcherQueue` 实现异步事件投递。跨进程事件总线基于 TCP Socket 通信，采用 JSON 消息协议实现多进程间的事件同步。

模块采用接口抽象设计，`EventBus` 定义统一契约，`LocalEventBus` 和 `InterProcessEventBus` 分别实现本地与跨进程能力。跨进程场景通过 `InterProcessServer` 作为中心节点管理客户端连接与订阅关系，支持心跳检测与自动重连机制。

## 直接依赖

```
com.cxuy.framework.eventbus
├── com.cxuy.framework.coroutine
│   └── queue.DispatcherQueue
├── com.cxuy.framework.annotation
│   └── @Nullable
├── com.cxuy.framework.util
│   ├── Logger
│   └── JsonUtil
└── com.cxuy.framework.eventbus.cross
    ├── InterProcessServer
    ├── protocol
    │   ├── Constants
    │   ├── Operation
    │   ├── Message
    │   └── data/protocol/message 子包
    └── decenter
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `EventBus` | 事件总线顶层接口，定义 register/unregister/post 契约 |
| `LocalEventBus` | 进程内事件总线实现，基于注解订阅者模式 |
| `InterProcessEventBus` | 跨进程事件总线实现，基于 TCP Socket 通信 |
| `InterProcessServer` | 跨进程事件服务端与连接管理器 |
| `InterProcessClientHandler` | 跨进程客户端连接处理器 |
| `SubscriberWrapper` | 订阅者方法包装器，封装事件类型与处理方法映射 |
| `Subscribe` | 标记方法为事件处理器的注解 |
| `Message` | 跨进程消息抽象，定义操作类型与编码规则 |
| `Operation` | 消息操作枚举：REGISTER/POST/UNREGISTER/HEARTBEAT 等 |
| `Constants` | 跨进程通信常量定义（端口、心跳间隔、超时等） |
