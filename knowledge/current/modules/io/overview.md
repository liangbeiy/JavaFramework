# IO 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.io |
| 业务域 | IO 操作 / 文件存储 / KV 存储 |
| 源文件数 | 16（含 file/kv 子模块） |

## 模块定位

IO 模块是 JavaFramework 的 IO 操作基础设施，提供统一的文件操作和键值存储能力。采用 Provider 模式设计，`FileProvider` 和 `KVProvider` 分别定义文件和 KV 存储的契约，`FileManager` 作为单例管理器提供线程安全的文件读写、创建、删除操作。

文件操作支持多路复用机制，通过 `FileExecutor` 实现共享/互斥访问模式，相同路径的操作自动合并到同一个执行器，避免并发冲突。KV 存储提供 `KVMap` 实现，支持单进程/多进程模式，数据持久化到本地 `.kv` 文件。

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
├── com.cxuy.framework.io.file
│   ├── FileProvider（接口）
│   ├── FileManager（单例）
│   ├── ReadFileProvider
│   ├── WriteFileProvider
│   ├── ExtraFileProvider
│   ├── execute.FileExecutor
│   └── exception.*（异常类）
└── com.cxuy.framework.io.kv
    ├── KVProvider
    ├── KVMap
    └── MapStorage（接口）
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `FileProvider` | 文件操作接口，定义 isExist/create/delete/mkdirs 契约 |
| `FileManager` | 文件管理器单例，提供线程安全的文件操作 |
| `FileExecutor` | 文件执行器，实现同路径操作的共享/互斥控制 |
| `ReadFileProvider` | 只读文件提供者实现 |
| `WriteFileProvider` | 只写文件提供者实现 |
| `ExtraFileProvider` | 扩展文件提供者，组合读写能力 |
| `KVProvider` | KV 存储提供者，管理多个 KVMap 实例 |
| `KVMap` | KV 存储实现，支持单/多进程模式 |
| `MapStorage` | KV 存储接口，定义 put/get/remove/contains |
| `FileManager.ReadFileCallback` | 读文件回调接口 |
| `FileManager.WriteFileCallback` | 写文件回调接口 |
