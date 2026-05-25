# IO 模块 数据模型

## 核心数据结构

### FileManager 内部状态

```java
public class FileManager implements FileExecutorIsEmptyCallback {
    private final Object transactionLock = new Object();
    private final Map<String, FileExecutor> transaction;  // 路径 → 执行器映射
    
    // 单例
    private static class HOLDER {
        private static final FileManager INSTANCE = new FileManager();
    }
}
```

### FileExecutor 内部状态

```java
public class FileExecutor {
    private final String path;
    private final FileManager.FileExecutorIsEmptyCallback callback;
    
    private final Object lock = new Object();
    private int shareCount = 0;          // 共享模式计数
    private boolean isMutexRunning = false; // 互斥模式是否运行中
    private final Queue<Runnable> mutexQueue; // 互斥任务队列
    private final Queue<Runnable> shareQueue; // 共享任务队列
}
```

### KVMap 数据模型

```java
public class KVMap implements MapStorage {
    private static final String FOLDER_NAME = "kv_map";
    private static final String EXTENSION_NAME_KV_MAP = ".kv";
    
    private final String filePath;  // 存储文件路径
    private final int mode;         // 存储模式（单进程/多进程）
    
    // 数据存储（当前为存根，待实现持久化）
    // 预期使用 Map<String, String> 内存缓存 + 文件持久化
}
```

### KVProvider 数据模型

```java
public class KVProvider implements Provider {
    // 存储名称 → MapStorage 实例映射
    public final Map<String, MapStorage> mapStorage = 
        Collections.synchronizedMap(new HashMap<>());
}
```

## 文件操作模式

### 共享模式（share）

```
多个读操作可并发执行
shareCount > 0 时，新的 share 任务可直接执行
shareCount = 0 时，最后一个共享任务完成后可进入互斥队列
```

### 互斥模式（mutex）

```
独占执行，等待所有共享任务完成
同一时间只有一个互斥任务执行
互斥任务按队列顺序执行（FIFO）
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| FileManager | 单例（HOLDER 内部类） | 全局唯一，进程级 |
| transaction Map | HashMap（同步锁保护） | 缓存路径→执行器映射，空时自动清理 |
| FileExecutor 任务队列 | LinkedList（内存） | 缓存待执行的共享/互斥任务 |
| KVProvider.mapStorage | synchronizedMap | 线程安全的存储实例缓存 |
| KVMap 数据 | 待实现 | 预期：内存缓存 + 文件持久化 |

## 数据流向

```
【文件读取流】
read(path, callback) → 获取/创建 FileExecutor
                            ↓
                     executor.share(Runnable)
                            ↓
                     Files.readAllBytes(path)
                            ↓
                     callback.callback(path, content)

【文件写入流】
write/append(path, content) → 获取/创建 FileExecutor
                            ↓
                     executor.mutex(Runnable)
                            ↓
                     确保父目录存在
                     确保文件存在
                            ↓
                     Files.write(path, content)
                            ↓
                     callback.callback(path, content, mode, result)

【KV 存储流】
KVProvider.mapStorage.get(name) → KVMap
                            ↓
                     kvMap.put(key, value)
                     kvMap.get(key, default)
                     kvMap.remove(key)
                            ↓
                     （待实现：写入 .kv 文件持久化）
```
