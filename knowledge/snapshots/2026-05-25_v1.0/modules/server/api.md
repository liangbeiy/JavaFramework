# Server 模块 API 文档

## 对外暴露接口

### Server 接口

```java
package com.cxuy.server;

public interface Server { }
// 标记接口，无方法定义
```

### SimpleHttpServer 类

```java
public class SimpleHttpServer implements Server, LifecycleOwner {
    private static final int DEFAULT_PORT = 5867;
    private static final int MAX_RETRY_TIMES = 10;
    
    public SimpleHttpServer(Context context);
    // 构造服务器实例，可添加生命周期观察者
    public SimpleHttpServer(Context context, LifecycleObserver observer);
    
    // 生命周期方法
    public void start();  // 启动服务器，自动分配端口（从 5867 开始重试）
    public void stop();   // 停止服务器
    
    // LifecycleOwner 接口实现
    public LifecycleState getState();
    public void setState(LifecycleState state);
    public void addObserver(LifecycleObserver observer);
    public void removeObserver(LifecycleObserver observer);
}
```

### Handler 基类

```java
public class Handler implements HttpHandler {
    protected final String prePath;  // 根路径（@HttpHandler.path()）
    private final Map<String, List<Method>> paths;  // 子路径 → 方法列表
    
    // 构造时扫描注解
    public Handler();
    
    // HttpHandler 接口方法
    public final void handle(HttpExchange exchange) throws IOException;
    
    // 获取处理器所有路径
    public final List<String> getHandlePath();
    
    // 子类可重写的方法
    protected String getRequestPath(HttpExchange exchange);
    protected Map<String, String> getQueryParameters(HttpExchange exchange);
    protected String getRequestBody(HttpExchange exchange);
    protected void response(HttpExchange exchange, HttpCode code, String response) throws IOException;
}
```

### JsonRpc 类

```java
public class JsonRpc<T> {
    public String timestamp;  // 时间戳（默认 yyyy-MM-dd HH:mm:ss）
    public String message;    // 消息
    public int code;          // 业务状态码
    public T data;            // 数据载荷
    
    // 静态工厂方法
    public static JsonRpc<Object> empty();
    public static JsonRpc<Object> failure();
    public static JsonRpc<Object> failure(BusinessCode code);
    public static <M> JsonRpc<M> successful(String message, M data);
    public static <M> JsonRpc<M> successful(M data);
    
    // 序列化/反序列化
    public String toJson();
    public static <M> JsonRpc<M> fromJson(String json);
    
    // Builder 模式
    public static class Builder<D> {
        public Builder<D> timestamp(String dateTime);
        public Builder<D> message(String message);
        public Builder<D> code(BusinessCode code);
        public Builder<D> code(int code);
        public Builder<D> data(D data);
        public JsonRpc<D> build();
    }
}
```

## 注解 API

### @HttpHandler

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpHandler {
    String path();  // 处理器根路径
}
```

### @HandlerMethod

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlerMethod {
    String subPath() default "";  // 子路径，拼接在根路径后
}
```

### @Param

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String value() default "";  // 参数名，空则使用方法参数名
}
```

### @RequestBody

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestBody { }
// 标记参数为请求体，自动反序列化
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| SimpleHttpServer | `new SimpleHttpServer(context)` | 手动调用 start/stop |
| Handler | `@HttpHandler` 注解 + HttpHandlerScanner 扫描 | Server 启动时自动创建并注册 |
| JsonRpc | 静态工厂方法或 Builder | 每次调用创建新实例 |

## REST/RPC API

### JSON RPC 响应格式

**成功响应：**
```json
{
  "timestamp": "2025-01-01 10:00:00",
  "message": "handle successful",
  "code": 200,
  "data": { ... }
}
```

**失败响应：**
```json
{
  "timestamp": "2025-01-01 10:00:00",
  "message": "failure",
  "code": 201,
  "data": null
}
```

### 状态码定义

**HttpCode（HTTP 状态码）：**
- `SUCCESSFUL(200)` - 成功
- `ERROR(400)` - 请求错误
- `NOT_FOUND(404)` - 未找到
- `SERVER_ERROR(500)` - 服务器错误
- `OUT_OF_SERVER(503)` - 服务不可用

**BusinessCode（业务状态码，基于 200 偏移）：**
- `SUCCESSFUL(0)` → 实际 200
- `FAILURE(1)` → 实际 201
- `PARAMS_ERROR(2)` → 实际 202
