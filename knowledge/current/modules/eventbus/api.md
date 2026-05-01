# EventBus 模块 API 文档

## 对外暴露接口

### EventBus 接口

```java
package com.cxuy.framework.eventbus;

public interface EventBus {
    void register(@Nullable Object subscriber);
    void unregister(@Nullable Object subscriber);
    void post(@Nullable Object event);
}
```

### LocalEventBus

```java
public class LocalEventBus implements EventBus {
    public static EventBus get();  // 单例获取
    
    // 继承 EventBus 接口方法
    void register(Object subscriber);   // 注册订阅者，扫描 @Subscribe 注解方法
    void unregister(Object subscriber); // 注销订阅者
    void post(Object event);           // 发布事件，异步分发至订阅者
}
```

### InterProcessEventBus

```java
public final class InterProcessEventBus implements EventBus {
    public static InterProcessEventBus get();  // 单例获取
    
    // 继承 EventBus 接口方法
    void register(Object subscriber);   // 注册并通知服务端
    void unregister(Object subscriber); // 注销并通知服务端
    void post(Object event);           // 发布事件至服务端转发
    
    // 内部方法
    void handlePendingMessage();  // 处理连接恢复后的挂起消息
}
```

## 注解 API

### @Subscribe

标记方法为事件处理器，方法需满足：
- 仅有一个参数（事件类型）
- 非静态方法

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe { }
```

## 跨进程协议 API

### Message 体系

```java
// 消息基类
public abstract class Message<T> {
    public final Operation operation;
    public final T data;
    
    public String encode();  // 编码为 JSON
    public static final String PARAMS_KEY_OPERATION = "operation";
    public static final String PARAMS_KEY_DATA = "data";
}

// 具体消息类型
RegisterMessage       // 注册事件类型
UnregisterMessage     // 注销事件类型
PostMessage           // 发布事件
HeartbeatCheckMessage // 心跳检查
HeartbeatResponseMessage // 心跳响应
```

### Operation 枚举

```java
public enum Operation {
    REGISTER, UNREGISTER, POST,
    HEARTBEAT_CHECK, HEARTBEAT_RESPONSE
}
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| LocalEventBus | `LocalEventBus.get()` 单例 | 进程级，随 JVM 存活 |
| InterProcessEventBus | `InterProcessEventBus.get()` 单例 | 自动连接服务端，支持重连 |
| InterProcessServer | `new InterProcessServer()` + `start()` | 需手动启停，遵循 Lifecycle |

## Bridge API（跨进程）

- **服务端监听端口**：`Constants.SOCKET_SERVER_START_PORT`（默认 0，系统分配）
- **消息格式**：JSON，含 `operation` 和 `data` 字段
- **心跳机制**：间隔 `HEARTBEAT_INTERVAL`，超时 `HEARTBEAT_TIMEOUT`
- **事件转发**：服务端维护 `Map<String, Set<InterProcessClientHandler>>` 订阅关系
