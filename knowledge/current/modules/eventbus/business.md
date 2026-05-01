# EventBus 模块 业务流程

## 本地事件总线流程

```
┌─────────────────────────────────────────────────────────────┐
│                    LocalEventBus 事件流                       │
└─────────────────────────────────────────────────────────────┘

  注册订阅者               发布事件                  事件处理
     │                        │                         │
     ▼                        ▼                         ▼
┌─────────┐            ┌─────────┐            ┌──────────────┐
│register │──async────▶│ 扫描   │            │ handleEvent  │
│(subscriber)│        │@Subscribe│            │(eventType,  │
└─────────┘            │ 方法   │            │ event)       │
                       └────┬────┘            └──────┬───────┘
                            │                       │
                            ▼                       ▼
                     ┌──────────────┐        ┌──────────┐
                     │SubscriberWrapper│      │method.   │
                     │  methods map  │       │invoke()  │
                     └──────┬───────┘        └──────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │subscribers  │
                     │Map<Class<?>, │
                     │ Set<Wrapper>>│
                     └──────────────┘
```

### 注册流程

1. 调用 `register(subscriber)`，提交至 `DispatcherQueue` 异步执行
2. 扫描订阅者类的所有方法，筛选带 `@Subscribe` 注解且参数数为 1 的方法
3. 构建 `SubscriberWrapper`，建立 `Map<Class<?>, Set<Method>>` 映射
4. 将 wrapper 存入 `registers`，并将事件类型注册到 `subscribers`

### 事件发布流程

1. 调用 `post(event)`，提交至 `DispatcherQueue` 异步执行
2. 根据 `event.getClass()` 查找 `subscribers` 中的 wrapper 集合
3. 遍历 wrappers，调用 `handleEvent(eventType, event)`
4. 通过反射 `method.invoke(subscriber, event)` 执行处理方法

## 跨进程事件总线流程

```
┌─────────────────────────────────────────────────────────────┐
│              InterProcessEventBus + Server 交互              │
└─────────────────────────────────────────────────────────────┘

  Client A                 Server                Client B
     │                        │                        │
     │── REGISTER(event) ────▶│                        │
     │                        │── 记录订阅关系 ────────▶
     │                        │                        │
     │── POST(event) ─────────▶│                        │
     │                        │── 查找订阅者 ──────────▶│
     │                        │                        │── handleEvent()
     │                        │                        │
     │◀── HEARTBEAT_CHECK ────│                        │
     │── HEARTBEAT_RESPONSE ─▶│                        │
```

### 连接管理

1. `InterProcessEventBus` 构造时自动调用 `connect()` 连接服务端
2. 连接失败或断开时，消息进入 `pendingMessages` 队列
3. 连接恢复后调用 `handlePendingMessage()` 处理挂起消息
4. 心跳机制：每 `HEARTBEAT_INTERVAL` 发送检查，超时则断开

### 服务端处理

1. `InterProcessServer.start()` 监听 `SOCKET_SERVER_START_PORT`
2. 接受客户端连接，创建 `InterProcessClientHandler` 处理
3. 维护 `subscribers` 映射：事件类型 → 客户端处理器集合
4. 收到 POST 消息时，查找对应事件类型的所有客户端并转发

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| subscriber 为 null | 直接返回，不执行任何操作 |
| 重复注册同一 subscriber | 检查 registers 已存在则跳过 |
| post null 事件 | 直接返回，不分发 |
| 事件无订阅者 | subscribers 中无记录，静默跳过 |
| 反射调用异常 | 捕获 IllegalAccessException/InvocationTargetException，Logger 记录 |
| Socket 连接失败 | 关闭 socket，Logger 记录，消息进入挂起队列 |
| 心跳超时 | 关闭 socket，触发重连流程 |
| 消息 JSON 解析失败 | 捕获异常，Logger 记录，跳过该消息 |
| 类找不到 (ClassNotFoundException) | Logger 记录，跳过该事件处理 |
