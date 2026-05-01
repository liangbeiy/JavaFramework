# Server 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 业务模块 |
| 包路径 | com.cxuy.server |
| 业务域 | HTTP 服务器 / 请求路由 |
| 源文件数 | 5 + annotation/model 子模块 |

## 模块定位

Server 模块是 JavaFramework 的 HTTP 服务器层，提供基于 `com.sun.net.httpserver` 的轻量级 HTTP 服务器实现。`SimpleHttpServer` 作为核心服务器类，自动扫描 `@HttpHandler` 注解的处理器类，并将请求路由到对应的 `Handler` 子类。

`Handler` 基类实现了完整的请求解析、参数绑定和响应生成逻辑，支持 `@HandlerMethod`、`@Param`、`@RequestBody` 等注解驱动开发。模块集成生命周期管理（`LifecycleOwner`），支持观察者模式监听服务器状态变化。响应格式采用 JSON RPC 规范，通过 `JsonRpc` 类封装。

## 直接依赖

```
com.cxuy.server
├── com.cxuy.framework.context
│   └── Context
├── com.cxuy.framework.lifecycle
│   ├── LifecycleOwner
│   ├── LifecycleObserver
│   └── LifecycleState
├── com.cxuy.framework.util
│   ├── Logger
│   ├── JsonUtil
│   └── TextUtils
├── com.cxuy.server.annotation
│   ├── HttpHandler（注解）
│   ├── HandlerMethod（注解）
│   ├── Param（注解）
│   └── RequestBody（注解）
└── com.cxuy.server.model
    └── JsonRpc
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `Server` | 服务器顶层接口（标记接口） |
| `SimpleHttpServer` | HTTP 服务器实现，自动扫描并注册 Handler |
| `Handler` | 请求处理器基类，实现请求解析、参数绑定、响应生成 |
| `HttpCode` | HTTP 状态码枚举（200/400/404/500/503） |
| `BusinessCode` | 业务状态码枚举（基于 200 偏移） |
| `JsonRpc` | JSON RPC 响应封装，Builder 模式构建 |
| `HttpHandlerScanner` | 注解扫描器，扫描 `@HttpHandler` 类 |
| `HttpHandler` | 标记类为 HTTP 处理器的注解 |
| `HandlerMethod` | 标记方法为处理方法的注解 |
| `Param` | 方法参数绑定注解 |
