# IO 模块 业务流程

## 文件操作流程

```
┌─────────────────────────────────────────────────────────────┐
│                  FileManager 文件操作                          │
└─────────────────────────────────────────────────────────────┘

  读文件                  写文件                  删除文件
     │                        │                        │
     ▼                        ▼                        ▼
┌─────────┐            ┌─────────┐            ┌──────────┐
│isExist()│            │isExist() │            │isExist()  │
└────┬────┘            └────┬────┘            └────┬─────┘
     │                      │                      │
     ▼                      ▼                      ▼
┌──────────────────────────────────────────────────────────┐
│           FileExecutor（同路径共享/互斥）                  │
│  share()  ── 读操作，多读者可并发                         │
│  mutex()  ── 写/删操作，独占执行                         │
└──────────────────────────────────────────────────────────┘
     │                      │                      │
     ▼                      ▼                      ▼
┌─────────┐            ┌─────────┐            ┌──────────┐
│Files.   │            │Files.   │            │file.     │
│readAll  │            │write()  │            │delete()  │
└─────────┘            └─────────┘            └──────────┘
```

### 文件读取流程

1. 调用 `read(path, callback)`，先检查文件是否存在
2. 通过 `transactionLock` 获取或创建该路径的 `FileExecutor`
3. 调用 `executor.share()` 进入共享模式（允许并发读）
4. 在共享块内调用 `Files.readAllBytes()` 读取文件内容
5. 将结果通过 `callback.callback(path, content)` 回调

### 文件写入流程

1. 调用 `write(path, content, callback)` 或 `append(path, content, callback)`
2. 确定写入模式：`MODE_WRITE`（覆盖）或 `MODE_APPEND`（追加）
3. 通过 `transactionLock` 获取或创建该路径的 `FileExecutor`
4. 调用 `executor.mutex()` 进入互斥模式（独占执行）
5. 确保父目录存在（不存在则 `mkdirs()`）
6. 确保文件存在（不存在则 `createNewFile()`）
7. 调用 `Files.write()` 或 `Files.writeString()` 写入内容
8. 通过 `callback.callback(path, content, mode, result)` 回调

### 文件删除流程

1. 调用 `delete(path)`
2. 通过 `transactionLock` 获取或创建该路径的 `FileExecutor`
3. 调用 `executor.mutex()` 进入互斥模式
4. 判断是文件还是目录：
   - 文件：直接 `file.delete()`
   - 目录：递归删除所有子文件和子目录
5. 删除后回调 `taskEmptyCallback()`，从 `transaction` Map 移除执行器

## 多路复用机制

```
同一路径的多次操作共享同一个 FileExecutor

transaction Map:  path → FileExecutor
                     │
                     ├── share(Runnable)  → 共享计数+1，并发执行
                     └── mutex(Runnable)  → 独占执行，等待所有共享完成

当 FileExecutor 任务为空时，回调 taskEmptyCallback() 从 Map 移除
```

## KV 存储流程

```
┌─────────────────────────────────────────────────────────────┐
│                    KVProvider + KVMap                        │
└─────────────────────────────────────────────────────────────┘

  获取/创建 KVMap
        │
        ▼
┌──────────────────────────────────┐
│ KVProvider                      │
│ mapStorage: Map<String, KVMap>  │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ KVMap                            │
│ filePath: .../kv_map/name.kv    │
│ mode: SINGLE_PROCESS/MULTI_PROCESS│
└────────┬─────────────────────────┘
         │
         ▼
   put/get/remove/contains
   （当前为存根实现，待完善）
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| 路径为 null | 标注 @NonNull，运行时会抛 NullPointerException |
| 文件不存在（读） | callback 返回 null |
| 父目录不存在（写） | 自动调用 `mkdirs()` 创建 |
| 父目录创建失败 | 抛出 `ParentFileException` |
| 文件创建失败 | 抛出 `CreateFileException` |
| 路径为目录（删除） | 递归删除所有子项 |
| 路径为文件（删除） | 直接删除 |
| FileExecutor 任务空 | 回调 `taskEmptyCallback()` 清理 |
| 并发写同一文件 | 通过同一 FileExecutor 互斥执行 |
| 并发读同一文件 | 通过同一 FileExecutor 共享执行 |
