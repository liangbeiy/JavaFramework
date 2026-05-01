# EventBus 模块 数据模型

## 核心数据结构

### SubscriberWrapper（本地/跨进程通用）

```java
// 位置：LocalEventBus.SubscriberWrapper / InterProcessEventBus.SubscriberWrapper
private static class SubscriberWrapper {
    private final Object subscriber;              // 订阅者实例
    private final Map<Class<?>, Set<Method>> methods;  // 事件类型 → 处理方法集
    
    public void handleEvent(Class<?> paramClass, Object event);
    public Set<Class<?>> getEvents();             // 返回订阅的所有事件类型
}
```

### InterProcessEventBus.SubscriberWrapper（扩展版）

```java
private static class SubscriberWrapper {
    private final Object subscriber;
    private final Map<Class<?>, Set<Method>> allMethods;     // 全部方法
    private final Map<Class<?>, Set<Method>> activeMethods;  // 已激活（服务端确认）方法
    
    public void activeMethod(Class<?> event);  // 激活某事件类型的方法
}
```

## 消息协议数据模型

### Message 体系

```java
// 消息基类
public abstract class Message<T> {
    public final Operation operation;
    public final T data;
    
    public String encode();  // 序列化为 JSON: {"operation": "...", "data": {...}}
}

// 注册消息
public class RegisterMessage extends Message<Set<String>> {
    // data: 事件类型的全限定类名集合
}

// 注销消息
public class UnregisterMessage extends Message<String> {
    public static final String DATA_UNREGISTER_ALL = "*";
    // data: 事件类型名 或 DATA_UNREGISTER_ALL
}

// 发布消息
public class PostMessage extends Message<PostData> { }

// PostData
public class PostData {
    public final String type;   // 事件类型的全限定类名
    public final String value;  // 事件对象的 JSON 序列化
    
    public static PostData create(Object event);
}

// 心跳消息
public class HeartbeatCheckMessage extends Message<Void> { }
public class HeartbeatResponseMessage extends Message<Void> { }
```

### Operation 枚举

```java
public enum Operation {
    REGISTER,           // 0: 注册事件类型
    UNREGISTER,         // 1: 注销事件类型
    POST,               // 2: 发布事件
    HEARTBEAT_CHECK,    // 3: 心跳检查
    HEARTBEAT_RESPONSE  // 4: 心跳响应
}
```

## 服务端数据模型

### InterProcessServer 订阅关系

```java
// 服务端维护的订阅表
Map<String, Set<InterProcessClientHandler>> subscribers;
// key: 事件类型的全限定类名
// value: 订阅该事件的所有客户端处理器
```

### PendingMessage（挂起消息）

```java
private record PendingMessage(Object poster, Message<?> message) { }
// 用于连接断开时缓存待发送的消息
Queue<PendingMessage> pendingMessages;
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| subscribers Map | 内存 HashMap | 进程内存储，无持久化 |
| registers Map | 内存 HashMap | 快捷查找注册者对应的 wrapper |
| pendingMessages | 内存 LinkedList | 连接断开时缓存，恢复后清空 |
| LocalEventBus | 单例，全局唯一 | 整个进程共享一个实例 |
| InterProcessEventBus | 单例（HOLDER 内部类） | 延迟初始化，全局唯一 |

## 数据流向

```
【本地事件流】
Subscriber -> register() -> subscribers Map -> post(event) -> 反射调用处理方法

【跨进程事件流】
Client A: post(event) -> JSON Message -> TCP Socket -> Server
                                                       │
Server: 解析 JSON -> 查找 subscribers -> 转发 JSON Message -> TCP Socket -> Client B
                                                               │
Client B: 接收 JSON -> 解析 event -> handleEvent() -> 反射调用
```
