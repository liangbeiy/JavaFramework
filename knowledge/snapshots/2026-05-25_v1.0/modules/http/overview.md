# HTTP 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 业务模块 |
| 包路径 | com.cxuy.http |
| 业务域 | HTTP 协议 / 客户端 |
| 源文件数 | 24（含 protocol/ client/ exception 子模块） |

## 模块定位

HTTP 模块是 JavaFramework 的 HTTP 客户端和协议封装层。`client` 子模块提供类似 OkHttp/Retrofit 的 HTTP 客户端能力，支持 GET/POST/PUT/DELETE 等方法，支持请求参数、请求体、文件上传和拦截器链。`protocol` 子模块实现声明式 HTTP API 定义，通过注解（@GET/@POST 等）和动态代理自动生成请求。

模块采用 Builder 模式构建请求，支持拦截器（Interceptor）在请求前后插入自定义逻辑。协议层通过 `Protocol` 和 `BuilderProvider` 实现注解驱动请求构建，支持 `@Body`、`@Param`、`@SubPath` 等参数注解。

## 直接依赖

```
com.cxuy.http
├── com.cxuy.framework.annotation
│   ├── @NonNull
│   └── @Nullable
├── com.cxuy.framework.coroutine
│   └── queue.DispatcherQueue（异步请求）
├── com.cxuy.framework.util
│   └── Logger
├── com.cxuy.framework.constant
│   └── Constant
├── com.cxuy.http.client
│   ├── Client、Request、Response、Executor
│   ├── Method（枚举）
│   └── Interceptor（接口）
├── com.cxuy.http.protocol
│   ├── Protocol、Encoder、Decoder
│   ├── BuilderProvider
│   ├── builder.*（各种 RequestBuilder）
│   └── annotation.*（@GET/@POST/@Body/@Param 等）
└── com.cxuy.http.exception
    └── InvalidMethodException
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `Client` | HTTP 客户端，支持同步/异步请求，拦截器链 |
| `Request` | 请求封装，Builder 模式构建 |
| `Response` | 响应封装，包含状态码、头、响应体 |
| `Executor` | 请求执行器，处理连接、请求体、响应解析 |
| `Method` | HTTP 方法枚举（GET/POST/PUT/DELETE 等） |
| `Interceptor` | 拦截器接口，在请求前后插入逻辑 |
| `Protocol` | 协议层，通过动态代理将注解接口转为请求 |
| `BuilderProvider` | 管理各种 RequestBuilder 单例 |
| `Encoder` | 编码器接口（请求体编码） |
| `Decoder` | 解码器接口（响应体解码） |
| `RequestBuilder` | 请求构建器接口，处理各种注解 |
