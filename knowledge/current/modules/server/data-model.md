# Server 模块 数据模型

## 核心数据结构

### SimpleHttpServer 内部状态

```java
public class SimpleHttpServer implements Server, LifecycleOwner {
    private static final String TAG = "SimpleServer";
    
    private static final int DEFAULT_PORT = 5867;
    private static final int MAX_RETRY_TIMES = 10;
    
    private int port = DEFAULT_PORT;
    private HttpServer server;  // JDK HttpServer 实例
    
    private final Object stateLock = new Object();
    private LifecycleState state = LifecycleState.ANY;
    
    private final Set<LifecycleObserver> observers;  // 线程安全集合
    
    private final Context context;  // 框架上下文
}
```

### Handler 内部状态

```java
public class Handler implements HttpHandler {
    private static final String TAG = "SimpleHttpServer.Handler";
    
    private static final String METHOD_GET = "GET";
    
    private final String prePath;  // 根路径（来自 @HttpHandler）
    private final Map<String, List<Method>> paths;  // subPath → 方法列表
    
    // 构造时扫描：
    // 1. 获取 @HttpHandler.path() → prePath
    // 2. 遍历当前类及父类（直到 Handler）的所有方法
    // 3. 筛选带 @HandlerMethod 的方法
    // 4. 按 subPath 分组存储到 paths
}
```

### JsonRpc 数据模型

```java
public class JsonRpc<T> {
    public String timestamp;  // 时间戳（格式：yyyy-MM-dd HH:mm:ss）
    public String message;    // 消息描述
    public int code;         // 业务状态码（基于 200 偏移）
    public T data;           // 数据载荷（泛型）
}
```

### HttpCode 枚举

```java
public enum HttpCode {
    SUCCESSFUL(200),  // 成功
    ERROR(400),        // 请求错误
    NOT_FOUND(404),    // 未找到
    SERVER_ERROR(500), // 服务器错误
    OUT_OF_SERVER(503); // 服务不可用
    
    public final int rawValue;
}
```

### BusinessCode 枚举

```java
public enum BusinessCode {
    SUCCESSFUL(0),   // 实际 200
    FAILURE(1),       // 实际 201
    PARAMS_ERROR(2);  // 实际 202
    
    private static final int BASE_CODE = 200;
    public final int rawValue;  // = BASE_CODE + code
}
```

## 注解数据模型

### @HttpHandler

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpHandler {
    String path();  // 存储处理器根路径
}
```

### @HandlerMethod

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlerMethod {
    String subPath() default "";  // 存储子路径
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
public @interface RequestBody {
    // 无属性，仅作为标记注解
}
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| Handler.paths | HashMap（构造时初始化） | 缓存 subPath → 方法列表，只读 |
| SimpleHttpServer.observers | synchronizedSet HashSet | 线程安全的观察者集合 |
| SimpleHttpServer.state | volatile + synchronized | 状态变量，支持并发读写 |
| JsonRpc | 每次新建 | 无缓存，每次调用创建新实例 |

## 数据流向

```
【请求处理流】
HTTP Request → HttpServer → Handler.handle()
                              ↓
                        getRequestPath() → 提取路径
                        getQueryParameters() → 提取查询参数
                        getRequestBody() → 提取请求体（非 GET）
                              ↓
                        dispatch(subPath, params, hasBody)
                              ↓
                        匹配 @HandlerMethod 方法
                        检查 @Param/@RequestBody 注解
                              ↓
                        convertParam() → 参数转换
                              ↓
                        method.invoke(this, params) → 执行处理方法
                              ↓
                        JsonRpc.successful(result).toJson()
                              ↓
                        response(exchange, 200, json)
                              ↓
                        设置 CORS 头 → 发送响应
```

```
【扫描注册流】
SimpleHttpServer.start()
         ↓
  registerHandler()
         ↓
  HttpHandlerScanner.scanClass(context)
         ↓
  扫描文件系统/JAR 包 → 查找 @HttpHandler 类
         ↓
  验证继承 Handler → 创建实例
         ↓
  handler.getHandlePath() → 获取所有路径
         ↓
  server.createContext(path, handler) → 注册到 JDK HttpServer
         ↓
  server.start() → 启动服务器
```

## JSON RPC 格式规范

### 成功响应结构

```json
{
  "timestamp": "2025-01-01 10:00:00",
  "message": "handle successful",
  "code": 200,
  "data": { ... }
}
```

### 失败响应结构

```json
{
  "timestamp": "2025-01-01 10:00:00",
  "message": "failure",
  "code": 201,
  "data": null
}
```

### 时间戳格式

- 默认格式：`yyyy-MM-dd HH:mm:ss`
- 由 `TimeUtil.FORMAT_DATETIME_SECOND` 定义
- 在 `JsonRpc` 构造时自动生成
