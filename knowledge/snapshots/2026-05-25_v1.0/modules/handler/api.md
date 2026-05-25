# Handler 模块 API 文档

## 对外暴露接口

### LoginHandler

```java
package com.cxuy.handler;

@HttpHandler(path = "/login")
public class LoginHandler extends Handler {
    @HandlerMethod
    public String login(@Param("username") String userName, 
                        @Param("pwd") String password) {
        return "successful";
    }
}
```

## 注解 API

### @HttpHandler

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpHandler {
    String path();  // 处理器根路径
}
```

### @HandlerMethod

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlerMethod {
    String subPath() default "";  // 子路径，拼接在根路径之后
}
```

### @Param

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String value() default "";  // 参数名，为空则使用方法参数名
}
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| LoginHandler | `@HttpHandler` 注解 + HttpHandlerScanner 扫描 | 由 SimpleHttpServer 创建实例并注册 |

## REST/RPC API

### LoginHandler 接口

```
路径：/login
方法：@HandlerMethod（默认 GET/POST 都支持）
参数：
  - username（通过 @Param("username") 绑定）
  - pwd（通过 @Param("pwd") 绑定）
返回：String "successful"（JSON RPC 格式）
```

## 扩展指南

创建新的 Handler 步骤：

1. 创建类继承 `Handler`
2. 添加 `@HttpHandler(path = "/your-path")` 注解
3. 添加方法并标注 `@HandlerMethod`
4. 方法参数使用 `@Param("name")` 或 `@RequestBody` 注解
5. 返回处理结果（自动序列化为 JSON RPC）
6. SimpleHttpServer 会自动扫描并注册
