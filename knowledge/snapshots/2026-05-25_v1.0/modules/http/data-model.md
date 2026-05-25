# HTTP 模块 数据模型

## 核心数据结构

### Request 内部状态

```java
public class Request {
    public final Method method;          // HTTP 方法
    public final String url;             // 请求 URL
    public final Map<String, Object> headers;  // 请求头
    public final Map<String, Object> params;    // 查询参数
    public final String body;             // 请求体（字符串）
    public final Map<String, File> files;      // 上传文件
    public boolean followRedirects;       // 是否跟随重定向
}
```

### Response 内部状态

```java
public class Response {
    public final int statusCode;          // HTTP 状态码
    public final String statusMessage;    // 状态消息
    public final Map<String, String> header;  // 响应头
    public final Body body;               // 响应体
    
    public static class Body {
        private static final Body EMPTY_BODY = new Body(new byte[0]);
        private final byte[] body;
        private String asString;  // 缓存的字符串形式
        
        public String string();  // 懒加载，转为 UTF-8 字符串
    }
}
```

### Client 内部状态

```java
public class Client {
    private static final String TAG = "Client";
    
    private int connectTimeout;
    private int responseTimeout;
    
    private final Object interceptorsLock = new Object();
    private final Set<Interceptor> interceptors;  // 拦截器集合
    
    // 单例
    private static class HOLDER {
        private static final Client INSTANCE = new Client();
    }
}
```

### Executor 内部状态

```java
public class Executor {
    private static final String BOUNDARY_PREFIX = "---------------------------";
    private static final String KEY_CONTENT_TYPE = "Content-Type";
    
    private final Request request;
    private final int connectTimeout;
    private final int readTimeout;
}
```

## 协议层数据模型

### Protocol 内部状态

```java
public final class Protocol implements InvocationHandler {
    private final String baseUrl;
    private final Encoder encoder;
}
```

### BuilderProvider 内部状态

```java
public class BuilderProvider {
    private final Object lock = new Object();
    private final Map<String, RequestBuilder> builders;
    
    // 默认注册的 Builder
    // - BodyBuilder (NAME = "Body")
    // - HeaderBuilder (NAME = "Header")
    // - MethodBuilder (NAME = "Method")
    // - ParamBuilder (NAME = "Param")
    // - SubPathBuilder (NAME = "SubPath")
}
```

### RequestBuilder 体系

```java
public interface RequestBuilder {
    void handleAnnotation(@NonNull Protocol protocol, 
                         @NonNull Annotation annotation, 
                         @Nullable Object value, 
                         @NonNull Request.Builder builder);
    boolean responds(Annotation annotation);
}

// 实现类
BodyBuilder、HeaderBuilder、MethodBuilder、ParamBuilder、SubPathBuilder
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| Client | 枚举单例（HOLDER） | 全局唯一，进程级 |
| BuilderProvider | 静态单例（HOLDER） | 全局唯一，初始化时注册默认 Builder |
| Response.Body | 懒加载字符串 | asString 缓存首次转换结果 |
| Client.interceptors | HashSet（同步锁保护） | 线程安全的拦截器集合 |

## 数据流向

```
【同步请求流】
Client.async(request, callback)
         ↓
  DispatcherQueue.io.async()
         ↓
  handleRequest(request)
         ↓
  Executor.execute()
         ↓
  HttpURLConnection 执行 HTTP 请求
         ↓
  Response(statusCode, headers, body)
         ↓
  callback.response(client, request, response)

【协议层流】
protocol.create(MyApi.class)
         ↓
  Proxy.newProxyInstance() → Protocol.invoke()
         ↓
  扫描方法注解 → Builder.handleAnnotation()
         ↓
  扫描参数注解 → Builder.handleAnnotation()
         ↓
  Request.Builder.build() → Request
         ↓
  返回 Request 给调用者

【拦截器流】
beforeSubmitRequest → submitRequest → beforeHandleRequest → 执行请求 → handleRequest
```
