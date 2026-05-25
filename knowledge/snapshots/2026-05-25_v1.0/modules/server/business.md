# Server 模块 业务流程

## 服务器启动流程

```
┌─────────────────────────────────────────────────────────────┐
│              SimpleHttpServer 启动流程                        │
└─────────────────────────────────────────────────────────────┘

  构造 SimpleHttpServer
        │
        ▼
  ┌──────────────────┐
  │setState(INITED)   │
  └────────┬─────────┘
           │
           ▼
  start()
           │
           ▼
  ┌──────────────────────────────────┐
  │ 循环创建 HttpServer（最多重试10次）│
  │ 从端口 5867 开始，失败则端口+1    │
  └────────┬─────────────────────────┘
           │
           ▼
  ┌──────────────────┐
  │ registerHandler() │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────────────────────┐
  │ HttpHandlerScanner.scanClass()    │
  │ 扫描 @HttpHandler 注解的类        │
  └────────┬─────────────────────────┘
           │
           ▼
  ┌──────────────────────────────────┐
  │ 验证是否继承 Handler               │
  │ 验证路径非空                       │
  │ 创建 Handler 实例                  │
  │ server.createContext(path, handler)│
  └────────┬─────────────────────────┘
           │
           ▼
  ┌──────────────────┐
  │server.start()     │
  │setState(DID_START)│
  └──────────────────┘
```

### 处理器注册流程

1. `HttpHandlerScanner.scanClass(context)` 扫描类路径
2. 支持文件系统扫描和 JAR 包扫描
3. 查找带 `@HttpHandler` 注解的类
4. 验证类是否继承 `Handler`
5. 获取注解中的路径信息
6. 通过反射创建 Handler 实例（支持内部类）
7. 调用 `handler.getHandlePath()` 获取所有处理路径
8. 为每个路径调用 `server.createContext(path, handler)`

## 请求处理流程

```
HTTP 请求到达
    │
    ▼
Handler.handle(HttpExchange)
    │
    ├── getRequestPath(exchange)        → 提取请求路径
    ├── getQueryParameters(exchange)     → 解析查询参数
    └── getRequestBody(exchange)        → 读取请求体（非 GET）
            │
            ▼
    dispatch(subPath, params, hasBody)
            │
            ├── 根据 subPath 查找方法列表
            ├── 遍历方法，调用 isMatchMethod() 匹配
            │   ├── 检查参数数量
            │   ├── 检查 @Param 注解参数
            │   └── 检查 @RequestBody 注解参数
            │
            ▼（找到匹配方法）
    convertParam(method, params, body)
            │
            ├── 遍历方法参数
            ├── @Param → params.get(name) → toValue()
            ├── @RequestBody → JsonUtil.fromJson(body, type)
            └── 无注解 → params.get(paramName) → toValue()
            │
            ▼
    method.invoke(this, params)
            │
            ▼
    JsonRpc.successful("handle successful", result)
            │
            ▼
    response(exchange, HttpCode.SUCCESSFUL, jsonRpc.toJson())
            │
            ├── 设置 CORS 头
            ├── 处理 OPTIONS 预检请求
            └── 发送响应（状态码 + body）
```

### 参数匹配流程

```
isMatchMethod(params, method, requestContainBody)
    │
    ├── 检查参数数量是否匹配
    │   └── methodParams.length == params.size() (或 +1 如果有 body)
    │
    ├── 遍历方法参数
    │   ├── @Param 参数 → 检查 params.containsKey(paramName)
    │   ├── @RequestBody 参数 → 检查 requestContainBody
    │   └── 无注解参数 → 按查询参数处理
    │
    └── 全部匹配 → 返回 true
```

## 生命周期状态转换

```
ANY → INITED → WILL_START → DID_START → WILL_STOP → DID_STOP
                               ↓
                        WILL_DESTROY → DID_DESTROY
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| 端口被占用 | 自动重试，端口号+1，最多重试 10 次 |
| 扫描到未继承 Handler 的类 | 记录警告日志，跳过 |
| 扫描到路径为空的类 | 记录警告日志，跳过 |
| 无法创建 Handler 实例 | 记录警告日志，跳过 |
| 请求路径无匹配处理器 | 返回 HTTP 400，JsonRpc.failure() |
| 方法参数不匹配 | 返回 null，查找下一个方法 |
| @Param 和 @RequestBody 共存 | 抛出 IllegalArgumentException |
| 方法执行异常 | 捕获 Exception，返回 HTTP 500 |
| GET 请求带请求体 | getRequestBody() 返回 null |
| OPTIONS 预检请求 | 返回 HTTP 200，空响应体 |
| 响应写入失败 | 抛出 IOException，由 HttpServer 处理 |

## CORS 支持

响应头自动设置：
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
```
