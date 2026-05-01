## 概述

JavaFramework 的网络通信分为两个层面：事件总线的跨进程通信和 HTTP 客户端架构。跨进程事件总线基于 TCP Socket 实现，采用自定义文本协议（JSON over TCP），支持注册、注销、事件投递和心跳检测。HTTP 客户端模块（com.cxuy.http）提供声明式 API 定义能力，通过注解配置请求方法、路径和参数。

网络协议设计遵循简单可扩展原则：所有消息封装为 `Message<T>` 对象，包含操作类型（Operation）、数据负载（T）和时间戳。序列化使用 Gson 库，消息以换行符分隔。

## 核心机制

### HTTP 客户端架构

HTTP 客户端位于 `com.cxuy.http` 包，采用 builder 模式构造请求：

**协议注解体系**：
- `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`: 定义 HTTP 方法
- `@Body`: 标记请求体参数
- `@Param`: 标记查询参数
- `@Header`: 标记请求头
- `@SubPath`: 定义子路径

**请求构建流程**：
1. `Protocol` 注解标记接口，定义基础路径
2. `RequestBuilder` 解析注解，生成请求
3. `Encoder`/`Decoder` 处理序列化/反序列化

### 事件总线跨进程协议

**InterProcessEventBus** (`com.cxuy.framework.eventbus.InterProcessEventBus:31-362`) 使用 localhost TCP 通信：

**连接管理**：
- 服务器地址：localhost: 常量定义（`Constants.SOCKET_SERVER_START_PORT`）
- 自动重连：心跳失败或 IO 异常触发重连
- 待处理队列：连接断开时缓存消息，恢复后重发

**消息处理循环** (`InterProcessEventBus:251-264`):
```java
private void startReceive() {
    interWorker.async(() -> {
        String line;
        while ((line = reader.readLine()) != null) {
            handleEvent(line); // 逐行读取并处理
        }
    });
}
```

### 消息格式

**Message<T>** (`com.cxuy.framework.eventbus.cross.protocol.Message:14-36`):
```java
public class Message<T> {
    public final Operation operation; // 操作类型
    public final T data;              // 数据负载
    public final long timestamp;      // 时间戳
    
    public String encode() {
        return JsonUtil.toJson(this); // 序列化为 JSON
    }
}
```

**Operation 枚举** (`com.cxuy.framework.eventbus.cross.protocol.Operation:8-17`):
```java
public enum Operation {
    REGISTER("register"),           // 注册订阅者
    UNREGISTER("unregister"),       // 注销订阅者
    POST("post"),                   // 发布事件
    HEARTBEAT_CHECK("heartbeat_check"),     // 心跳检查
    HEARTBEAT_RESPONSE("heartbeat_response"); // 心跳响应
}
```

**PostData** (`com.cxuy.framework.eventbus.cross.protocol.data.PostData:11-26`):
```java
public final class PostData {
    public final String type;   // 事件类型的全限定类名
    public final String value;  // JSON 序列化的事件对象
    
    public static <T> PostData create(@NonNull T element) {
        return new PostData(element.getClass().getName(), JsonUtil.toJson(element));
    }
}
```

**消息示例**（JSON 格式）：
```json
{
  "operation": "post",
  "data": {
    "type": "com.cxuy.event.UserLoginEvent",
    "value": "{\"userId\":123,\"name\":\"test\"}"
  },
  "timestamp": 1234567890
}
```

### 心跳机制

心跳检测确保连接有效性 (`InterProcessEventBus:308-315`):
1. 定时发送 `HeartbeatCheckMessage`（间隔 `HEARTBEAT_INTERVAL`）
2. 设置超时任务（`HEARTBEAT_TIMEOUT`），超时未收到响应则关闭连接
3. 收到 `HeartbeatResponseMessage` 取消超时任务，重新调度下次检查

## 关键类/模块

| 类名 | 包路径 | 职责 |
|------|--------|------|
| InterProcessEventBus | framework.eventbus | 跨进程事件总线，TCP Socket 实现 |
| Message | framework.eventbus.cross.protocol | 消息封装类，泛型数据负载 |
| Operation | framework.eventbus.cross.protocol | 操作类型枚举 |
| PostData | framework.eventbus.cross.protocol.data | 事件数据封装，包含类型和 JSON 值 |
| RegisterMessage | framework.eventbus.cross.protocol.message | 注册消息 |
| PostMessage | framework.eventbus.cross.protocol.message | 发布事件消息 |
| HeartbeatCheckMessage | framework.eventbus.cross.protocol.message | 心跳检查消息 |
| HeartbeatResponseMessage | framework.eventbus.cross.protocol.message | 心跳响应消息 |
| Constants | framework.eventbus.cross.protocol | 协议常量（端口、间隔、超时） |
| InterProcessServer | framework.eventbus.cross | 跨进程服务器（未展示） |

## 约束与注意事项

1. **TCP 连接限制**：仅支持 localhost 通信，不支持跨机器部署（`InterProcessEventBus:33`）
2. **消息分隔符**：使用换行符 `\n` 分隔消息，消息内容不能包含换行（`InterProcessEventBus:152`）
3. **JSON 序列化依赖**：所有消息使用 Gson 序列化，需确保数据类型可序列化
4. **心跳超时处理**：心跳超时触发连接关闭，待发送消息进入 pendingQueue（`InterProcessEventBus:308-314`）
5. **事件类型解析**：接收方通过 `Class.forName(postData.type)` 反序列化，需确保类在 classpath 中（`InterProcessEventBus:181`）
6. **线程模型**：接收消息使用 `interWorker`，发送消息使用 `worker`，避免阻塞（`InterProcessEventBus:41-42`）
