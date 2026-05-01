# Handler 模块 业务流程

## 请求处理流程

```
┌─────────────────────────────────────────────────────────────┐
│              Handler 请求处理（以 LoginHandler 为例）          │
└─────────────────────────────────────────────────────────────┘

  HTTP 请求                   SimpleHttpServer
     │                              │
     ▼                              ▼
┌─────────┐                  ┌──────────────┐
│POST      │─────────────────▶│server.      │
│/login    │                  │createContext │
│?username │                  │(path,       │
│=xxx&pwd= │                  │handler)     │
└─────────┘                  └──────┬───────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │Handler.      │
                             │handle()      │
                             └──────┬───────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │dispatch()    │
                             │根据 subPath  │
                             │和参数匹配    │
                             └──────┬───────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │login()       │
                             │@HandlerMethod│
                             └──────┬───────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │return        │
                             │"successful"  │
                             └──────┬───────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │JsonRpc.      │
                             │successful()  │
                             │包装返回      │
                             └──────────────┘
```

### LoginHandler 处理流程

1. 客户端发送 POST `/login?username=xxx&pwd=yyy`
2. SimpleHttpServer 分发到 LoginHandler.handle()
3. Handler 基类解析路径，调用 `dispatch()` 匹配方法
4. 找到 `login()` 方法，检查参数匹配（@Param 注解）
5. 通过 `convertParam()` 将请求参数转换为方法参数
6. 反射调用 `login(userName, password)`
7. 返回结果被包装为 `JsonRpc.successful(result).toJson()`
8. 发送 HTTP 响应（状态码 200 + JSON 体）

## 参数绑定流程

### @Param 绑定

```
请求参数：?username=admin&pwd=123456
     │
     ▼
Handler.convertParam()
     │
     ├── 参数1: @Param("username") String userName
     │   → params.get("username") → "admin"
     │   → toValue("admin", String.class) → "admin"
     │
     └── 参数2: @Param("pwd") String password
         → params.get("pwd") → "123456"
         → toValue("123456", String.class) → "123456"
```

### @RequestBody 绑定

```
请求体：{"key": "value"}
     │
     ▼
Handler.convertParam()
     │
     └── 参数: @RequestBody MyObject obj
         → JsonUtil.fromJson(body, MyObject.class)
         → 反序列化为 MyObject 实例
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| 路径未找到 | 返回 HTTP 400，JsonRpc.failure() |
| 方法未找到 | 记录警告日志，返回 HTTP 400 |
| 参数不匹配 | 返回 null 或默认值 |
| @Param 和 @RequestBody 共存 | 抛出 IllegalArgumentException |
| 方法执行异常 | 捕获 Exception，返回 HTTP 500 |
| GET 请求带 @RequestBody | 返回 null，方法执行时可能 NPE |
| 参数类型转换失败 | 抛出 NumberFormatException 等 |

## 状态机

```
Handler 生命周期：
构造（扫描注解） → 注册到 Server → 等待请求 → 处理请求 → 返回响应
```
