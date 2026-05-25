# Handler 模块 数据模型

## 核心数据结构

### Handler 内部状态（来自 server 模块）

```java
public class Handler implements HttpHandler {
    private final String prePath;  // 根路径（@HttpHandler.path()）
    private final Map<String, List<Method>> paths;  // 子路径 → 方法列表
}
```

### LoginHandler 数据模型

```java
@HttpHandler(path = "/login")
public class LoginHandler extends Handler {
    // 无实例字段，所有状态来自父类
    
    @HandlerMethod
    public String login(@Param("username") String userName, 
                        @Param("pwd") String password) {
        return "successful";
    }
}
```

## 注解数据模型

### @HttpHandler

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpHandler {
    String path();  // 存储路径信息，运行时可读
}
```

### @HandlerMethod

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlerMethod {
    String subPath() default "";  // 存储子路径，默认空字符串
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
    // 标记参数为请求体，无属性
}
```

## 数据流向

```
【请求处理流】
HTTP Request → SimpleHttpServer → Handler.handle()
                                    ↓
                              getRequestPath() → 提取路径
                              getQueryParameters() → 提取查询参数
                              getRequestBody() → 提取请求体
                                    ↓
                              dispatch(subPath, params, hasBody)
                                    ↓
                              匹配 @HandlerMethod 方法
                              检查 @Param/@RequestBody 注解
                                    ↓
                              convertParam() → 参数转换
                                    ↓
                              method.invoke(this, params)
                                    ↓
                              JsonRpc.successful(result).toJson()
                                    ↓
                              response(exchange, 200, json)
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| Handler.paths | HashMap（构造时初始化） | 缓存子路径→方法列表映射，只读 |
| LoginHandler | 单例（由 Server 创建） | 每次请求创建新实例或复用 |

## JSON RPC 格式

### 成功响应

```json
{
  "code": 200,
  "message": "handle successful",
  "data": "successful"
}
```

### 失败响应

```json
{
  "code": 201,
  "message": "failure",
  "data": null
}
```

（注：具体格式由 `JsonRpc` 类定义，位于 server.model 包）
