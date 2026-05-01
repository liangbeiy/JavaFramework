# Handler 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 业务模块 |
| 包路径 | com.cxuy.handler |
| 业务域 | 请求处理器 / 业务逻辑 |
| 源文件数 | 1 |

## 模块定位

Handler 模块是 JavaFramework 的业务处理器层，提供具体的请求处理逻辑。当前实现了 `LoginHandler` 作为示例，展示如何使用 `@HttpHandler` 和 `@HandlerMethod` 注解定义 HTTP 接口。

模块依赖 server 模块提供的 `Handler` 基类和注解体系，通过注解驱动的方式将 HTTP 请求路由到具体的处理方法。处理器方法支持参数自动绑定（通过 `@Param` 注解）和处理逻辑封装，返回结果自动序列化为 JSON RPC 格式。

## 直接依赖

```
com.cxuy.handler
├── com.cxuy.server
│   ├── Handler（基类）
│   ├── annotation.HttpHandler
│   ├── annotation.HandlerMethod
│   └── annotation.Param
└── com.cxuy.server.annotation
    ├── HttpHandler（注解）
    ├── HandlerMethod（注解）
    └── Param（注解）
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `LoginHandler` | 登录处理器示例，处理 `/login` 路径的 POST 请求 |
