# Util 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.util |
| 业务域 | 通用工具类 |
| 源文件数 | 5 |

## 模块定位

Util 模块是 JavaFramework 的通用工具类集合，提供文本处理、JSON 序列化、日志输出、时间处理和对象缓存池等基础能力。`TextUtils` 提供空值检查和字符串比较，`JsonUtil` 封装 Gson 库提供类型安全的 JSON 序列化/反序列化，`Logger` 实现分级日志系统并支持多 Handler 输出。

`TimeUtil` 提供时间格式化工具，支持 ISO、常见日期时间格式。`CachePool` 实现泛型缓存池，基于双向链表维护 LRU 顺序，支持异步操作和删除监听器。模块设计为静态方法调用或单例模式，无状态依赖，可被其他模块自由引用。

## 直接依赖

```
com.cxuy.framework.util
├── com.cxuy.framework.annotation
│   ├── @NonNull
│   └── @Nullable
├── com.cxuy.framework.io.file
│   └── FileManager（Logger 使用）
├── com.cxuy.framework.coroutine
│   └── queue.DispatcherQueue（CachePool 使用）
├── com.google.gson（外部）
│   └── Gson、GsonBuilder 等
└── java.time（JDK）
    └── LocalDateTime、DateTimeFormatter
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `TextUtils` | 文本工具，提供 isEmpty/equals 静态方法 |
| `JsonUtil` | JSON 工具，封装 Gson，支持泛型反序列化 |
| `Logger` | 日志系统，支持 VERBOSE/DEBUG/INFO/WARN/ERROR/ASSERT 级别 |
| `TimeUtil` | 时间工具，提供多种格式化输出 |
| `CachePool` | 泛型缓存池，LRU 策略，异步操作 |
| `Logger.Handler` | 日志处理器接口（函数式接口） |
| `Logger.Level` | 日志级别枚举 |
| `Logger.PrintHandler` | 默认控制台打印处理器 |
| `CachePool.CachePoolCheckCallback` | 缓存查询回调接口 |
| `CachePool.ItemHasDeleted` | 缓存项删除回调接口 |
