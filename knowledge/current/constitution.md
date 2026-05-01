# Project Constitution

## Project Overview

**Name**: JavaFramework
**Group**: com.cxuy
**Version**: 1.0-SNAPSHOT
**Description**: 一个 Java 脚手架项目，提供事件总线、协程调度、IO 操作、HTTP 客户端等基础框架能力。

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | (待项目组确认 JDK 版本) | Primary language |
| Gradle | (待项目组确认) | Build system |
| JUnit Jupiter | 5.10.0 | Testing framework |
| Gson | 2.13.1 | JSON serialization |

## Module List

| Module | Package | Description |
|--------|---------|-------------|
| framework | com.cxuy.framework | 核心框架模块，包含事件总线、协程、IO、上下文管理等 |
| server | com.cxuy.server | 服务器组件 |
| http | com.cxuy.http | HTTP 协议与客户端 |
| handler | com.cxuy.handler | 请求处理器（如 LoginHandler） |

### Sub-modules in framework

| Sub-module | Package | Description |
|------------|---------|-------------|
| eventbus | com.cxuy.framework.eventbus | 事件总线（本地+跨进程） |
| coroutine | com.cxuy.framework.coroutine | 协程调度框架 |
| io | com.cxuy.framework.io | IO 操作（文件/KV存储） |
| context | com.cxuy.framework.context | 框架上下文 |
| util | com.cxuy.framework.util | 工具类 |
| lifecycle | com.cxuy.framework.lifecycle | 生命周期管理 |
| annotation | com.cxuy.framework.annotation | 注解处理器 |
| livedata | com.cxuy.framework.livedata | 响应式数据 |
| struct | com.cxuy.framework.struct | 数据结构 |
| constant | com.cxuy.framework.constant | 常量定义 |
| provider | com.cxuy.framework.provider | 提供者框架 |

## Architecture Constraints

1. 框架模块（framework）是基础模块，不被其他业务模块反向依赖
2. handler 模块依赖 framework 提供的事件总线和处理能力
3. http 和 server 模块可独立使用，也可组合使用
4. 所有跨进程通信必须通过 eventbus.cross 包提供的协议
5. 协程调度器（coroutine）禁止在 IO 线程执行阻塞操作
6. KV 存储（io.kv）和文件操作（io.file）应提供统一的 Provider 接口
7. 注解处理器（annotation.processor）在编译期生成代码，运行时无反射开销
8. 版本依赖必须锁定（Gradle dependency locking）

## Dependency Direction

```
framework (base)
    ↑
    |
handler / http / server
```

## Build Commands

| Action | Command |
|--------|---------|
| Compile | `./gradlew compileJava` |
| Test | `./gradlew test` |
| Lint | `./gradlew check` |
| Build | `./gradlew build` |
