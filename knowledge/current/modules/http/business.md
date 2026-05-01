# HTTP 模块 业务流程

## 同步请求流程

```
┌─────────────────────────────────────────────────────────────┐
│                  Client 同步请求                                 │
└─────────────────────────────────────────────────────────────┘

  Client.async(request, callback)
        │
        ▼
  ┌──────────────────────────────────┐
  │ 拦截器.beforeSubmitRequest()       │
  │ 拦截器.submitRequest(async=true)   │
  └──────────────┬───────────────────┘
                 │
                 ▼
  ┌──────────────────────────────────┐
  │ DispatcherQueue.io.async()        │
  │ （异步执行，不阻塞调用线程）        │
  └──────────────┬───────────────────┘
                 │
                 ▼
  ┌──────────────────────────────────┐
  │ handleRequest(request)              │
  │   → 拦截器.beforeHandleRequest()  │
  │   → Executor.execute()             │
  │   → 拦截器.handleRequest()        │
  └──────────────┬───────────────────┘
                 │
                 ▼
  ┌──────────────────────────────────┐
  │ callback.response(client,          │
  │   request, response)              │
  └──────────────────────────────────┘
```

### Executor 请求执行流程

```
Executor.execute()
    │
    ▼
┌──────────────────────────────────┐
│ buildUrl()                       │
│ URL + ?param1=value1&...         │
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ config(connection)                │
│  - setRequestMethod              │
│  - setConnectTimeout             │
│  - setReadTimeout                │
│  - setInstanceFollowRedirects    │
│  - 设置请求头                     │
│  - 设置 DoOutput（如有请求体/文件）│
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ handleRequestBody(connection)      │
│  有文件 → handleMultipartData     │
│  有参数 → handleFormData          │
│  有 body → handleRawBody          │
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ request(connection)                │
│  - connection.getResponseCode()   │
│  - 读取响应头                     │
│  - 读取响应体（正常/错误流）       │
│  → new Response(statusCode, ...)  │
└──────────────────────────────────┘
```

## 协议层（动态代理）流程

```
MyApi api = protocol.create(MyApi.class);
Request request = api.getUser("123");
        │
        ▼
┌──────────────────────────────────┐
│ Protocol.invoke(proxy, method, args)│
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ 处理方法上的注解                   │
│ @GET(path="/users/{id}")          │
│   → GET.Builder.handleAnnotation() │
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ 处理方法参数的注解                  │
│ @SubPath("id") String userId     │
│   → SubPath.Builder.handleAnnotation()│
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│ Request.Builder.build()           │
│ → Request 实例                   │
└──────────────────────────────────┘
```

## 拦截器链流程

```
拦截器1.beforeSubmitRequest()
拦截器2.beforeSubmitRequest()
...
拦截器1.submitRequest()
拦截器2.submitRequest()
...
拦截器1.beforeHandleRequest()
拦截器2.beforeHandleRequest()
...
执行请求
...
拦截器1.handleRequest()
拦截器2.handleRequest()
...
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| request 为 null | async() 直接返回，不执行 |
| 连接超时 | HttpURLConnection 抛出 IOException，返回 null |
| 响应码 >= 400 | 读取 errorStream 而非 inputStream |
| 无效 HTTP 方法 | 抛出 InvalidMethodException |
| URL 语法错误 | 捕获 URISyntaxException，返回 null |
| 文件上传 | 使用 multipart/form-data，随机 boundary |
| 表单提交 | 使用 application/x-www-form-urlencoded |
| 拦截器为 null | add/remove 时直接返回 |
| 协议接口验证 | 当前 isVail() 恒返回 true（待完善） |

## 状态机

```
Client 状态：
初始 → 运行中（可提交请求）→ 无显式关闭（随 JVM 退出）
```
