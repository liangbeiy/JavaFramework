# IO 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.io |
| 业务域 | IO 操作 / 文件存储 / KV 存储 |
| 源文件数 | 20+（含 file/kv 子模块） |

## 模块定位

IO 模块是 JavaFramework 的 IO 操作基础设施，提供统一的文件操作和键值存储能力。采用 Provider 模式设计，按职责拆分为 `FileProvider`（基础契约）、`ReadFileProvider`（读取操作）、`WriteFileProvider`（写入操作）和 `ExtraFileProvider`（扩展操作如 copy/move）。

`FileManagerV2` 为推荐的文件管理器实现，提供完整的文件读写、删除、目录创建能力。`FileManager`（旧版）已标记 `@Deprecated`，将在后续版本移除。

文件操作通过 `FileExecutor` 实现共享/互斥访问控制，底层使用 NIO.2 `FileLock` 实现真正的跨进程文件锁（旧版基于 Semaphore）。同路径操作自动合并到同一个执行器，任务空闲时自动关闭执行器释放资源。

KV 存储提供 `KVMap` 实现，支持单进程/多进程模式，数据持久化到本地 `.kv` 文件。

## 直接依赖

```
com.cxuy.framework.io
├── com.cxuy.framework.provider
│   └── Provider（接口）
├── com.cxuy.framework.annotation
│   ├── @NonNull
│   └── @Nullable
├── com.cxuy.framework.util
│   ├── Logger
│   └── TextUtils
├── com.cxuy.framework.coroutine
│   └── Coroutines（FileExecutor 异步调度使用）
├── com.cxuy.framework.io.file
│   ├── FileProvider（基础接口，定义 isExist/create/delete/mkdirs）
│   ├── ReadFileProvider（读取接口，继承 FileProvider）
│   ├── WriteFileProvider（写入接口，继承 ReadFileProvider）
│   ├── ExtraFileProvider（扩展接口，继承 FileProvider，定义 copy/move）
│   ├── FileManager（@Deprecated，旧版实现）
│   ├── FileManagerV2（推荐实现，实现 WriteFileProvider）
│   ├── execute.FileExecutor（NIO.2 FileLock 锁机制）
│   └── exception.*（异常类）
└── com.cxuy.framework.io.kv
    ├── KVProvider
    ├── KVMap
    ├── SimpleKV
    └── MapStorage（接口）
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `FileProvider` | 文件操作基础接口，定义 isExist/create/delete/mkdirs 契约 |
| `ReadFileProvider` | 只读文件接口，定义 readString/readBytes 及 ReadFileCallback |
| `WriteFileProvider` | 写入文件接口，继承 ReadFileProvider，定义 write/append 及 WriteFileCallback |
| `ExtraFileProvider` | 扩展文件接口，定义 copy/move 操作 |
| `FileManagerV2` | 推荐的文件管理器实现，实现 WriteFileProvider |
| `FileManager` | 旧版文件管理器（@Deprecated），回调签名已适配 |
| `FileExecutor` | 文件执行器，NIO.2 FileLock 实现共享/互斥控制 |
| `KVProvider` | KV 存储提供者，管理多个 KVMap 实例 |
| `KVMap` | KV 存储实现，支持单/多进程模式 |
| `SimpleKV` | 简单 KV 存储实现，文件持久化 |
| `MapStorage` | KV 存储接口，定义 put/get/remove/contains |
| `NotExistsPathException` | 路径不存在异常，继承 RuntimeException |

## 变更提示

- `FileProvider` 接口已收缩，读写方法移至 `ReadFileProvider`/`WriteFileProvider`
- `FileManager` 已标记 `@Deprecated`，推荐使用 `FileManagerV2`
- `FileExecutor` 锁机制从 Semaphore 升级为 NIO.2 `FileLock`
- `ReadFileCallback` 签名改为 3 参数 `(path, result, content)`，增加 result 参数
- `FileExecutorIsEmptyCallback.taskEmptyCallback` 签名改为 `(executor, path)`
