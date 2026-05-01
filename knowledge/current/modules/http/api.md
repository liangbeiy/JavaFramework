# HTTP 模块 API 文档

## 对外暴露接口

### Client 类

```java
public class Client {
    public static final int DEFAULT_TIMEOUT_CONNECT = 5000;
    public static final int DEFAULT_TIMEOUT_RESPONSE = 10000;
    
    public interface ResponseCallback {
        void response(@NonNull Client client, @NonNull Request request, Response response);
    }
    
    public static Client getInstance();  // 单例获取
    
    public Client();
    public Client(int timeout);  // 同时设置连接和响应超时
    public Client(int connectTimeout, int responseTimeout);
    
    // 拦截器管理
    public void addInterceptor(Interceptor interceptor);
    public void removeInterceptor(Interceptor interceptor);
    
    // 异步请求
    public void async(@NonNull Request request, ResponseCallback callback);
    
    // 内部同步请求（private）
    private Response sync(@NonNull Request request);
}
```

### Request 类

```java
public class Request {
    public final Method method;
    public final String url;
    public final Map<String, Object> headers;
    public final Map<String, Object> params;
    public final String body;
    public final Map<String, File> files;
    public boolean followRedirects;
    
    // Builder 模式
    public static class Builder {
        public Builder method(Method method);
        public Builder url(String url);
        public Builder setSubPath(String key, String subPath);  // 替换 URL 中的 {key}
        public Builder header(Map<String, Object> headers);
        public Builder params(Map<String, Object> params);
        public Builder body(String body);
        public Builder file(Map<String, File> files);
        public Builder followRedirects(boolean followRedirects);
        public Builder addHeader(String key, Object value);
        public Builder addParam(String key, Object value);
        public Builder addFile(String key, File value);
        public Request build();
    }
}
```

### Response 类

```java
public class Response {
    public final int statusCode;
    public final String statusMessage;
    public final Map<String, String> header;
    public final Body body;
    
    public static class Body {
        public String string();  // 转为 UTF-8 字符串
    }
}
```

### Method 枚举

```java
public enum Method {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH
}
```

### Interceptor 接口

```java
public interface Interceptor {
    default void beforeSubmitRequest(Client client, Request request) {}
    default void submitRequest(Client client, Request request, boolean async) {}
    default void beforeHandleRequest(Client client, Request request) {}
    default void handleRequest(Client client, Request request, Response response) {}
}
```

## 协议层 API

### Protocol 类（声明式 HTTP）

```java
public final class Protocol implements InvocationHandler {
    public Protocol(String baseUrl, Encoder encoder);
    
    public <T> T create(Class<T> clazz);  // 创建动态代理实例
    public String getBaseUrl();
    public Encoder getEncoder();
    
    // Builder 模式
    public static final class Builder {
        public Builder baseUrl(String url);
        public Builder decoder(Encoder decoder);
        public Protocol build();
    }
}
```

### 注解 API

```java
// 方法注解（定义 HTTP 方法）
@Target(ElementType.METHOD)
public @interface GET { String path(); }

@Target(ElementType.METHOD)
public @interface POST { String path(); }

// 参数注解
@Target(ElementType.PARAMETER)
public @interface Param { String value(); }

@Target(ElementType.PARAMETER)
public @interface Body { }  // 标记参数为请求体

@Target(ElementType.PARAMETER)
public @interface SubPath { String value(); }  // 替换 URL 中的 {key}
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| Client | `Client.getInstance()` 单例 | 进程级，随 JVM 存活 |
| BuilderProvider | `BuilderProvider.getInstance()` 单例 | 进程级，初始化时注册默认 Builder |
| Protocol | `new Protocol(baseUrl, encoder)` | 由使用者管理，通常每个 BaseUrl 一个实例 |

## REST/RPC API（协议层使用示例）

```java
// 定义接口
public interface MyApi {
    @GET(path = "/users/{id}")
    Request getUser(@SubPath("id") String userId);
    
    @POST(path = "/login")
    Request login(@Param("username") String username, @Param("password") String password);
}

// 创建实例
Protocol protocol = new Protocol.Builder()
    .baseUrl("https://api.example.com")
    .build();
MyApi api = protocol.create(MyApi.class);

// 使用
Request request = api.getUser("123");
Response response = Client.getInstance().async(request, callback);
```
