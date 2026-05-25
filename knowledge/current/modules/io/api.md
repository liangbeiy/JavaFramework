# IO 模块 API 文档

## 对外暴露接口

### FileProvider 接口（基础契约）

```java
package com.cxuy.framework.io.file;

public interface FileProvider extends Provider {
    String NAME = "FileProvider";
    
    boolean isExist(String path);
    void create(String path);
    void delete(String path);
    void mkdirs(String path);
}
```

### ReadFileProvider 接口（读取操作）

```java
public interface ReadFileProvider extends FileProvider {
    String NAME = "ReadFileProvider";
    
    @FunctionalInterface
    interface ReadFileCallback<R> {
        void callback(String path, boolean result, R content);
    }
    
    void readString(String path, ReadFileCallback<String> callback);
    void readBytes(String path, ReadFileCallback<byte[]> callback);
}
```

### WriteFileProvider 接口（写入操作）

```java
public interface WriteFileProvider extends ReadFileProvider {
    String NAME = "WriteFileProvider";
    
    int MODE_WRITE = 0;
    int MODE_APPEND = 1;
    
    @FunctionalInterface
    interface WriteFileCallback {
        void callback(String path, int mode, boolean result);
    }
    
    void write(String path, String content, WriteFileCallback callback);
    void write(String path, byte[] bytes, WriteFileCallback callback);
    void append(String path, String content, WriteFileCallback callback);
    void append(String path, byte[] bytes, WriteFileCallback callback);
}
```

### ExtraFileProvider 接口（扩展操作）

```java
public interface ExtraFileProvider extends FileProvider {
    String NAME = "ExtraFileProvider";
    
    void copy(String sourcePath, String destinationPath);
    void move(String sourcePath, String destinationPath);
}
```

### FileManagerV2 单例（推荐实现）

```java
public class FileManagerV2 implements WriteFileProvider, FileExecutorIsEmptyCallback {
    public static FileManagerV2 getInstance();
    
    // 继承自 ReadFileProvider
    public void readString(String path, ReadFileCallback<String> callback);
    public void readBytes(String path, ReadFileCallback<byte[]> callback);
    
    // 继承自 WriteFileProvider
    public void write(String path, String content, WriteFileCallback callback);
    public void write(String path, byte[] bytes, WriteFileCallback callback);
    public void append(String path, String content, WriteFileCallback callback);
    public void append(String path, byte[] bytes, WriteFileCallback callback);
    
    // 继承自 FileProvider
    public boolean isExist(String path);
    public void create(String path);
    public void delete(String path);
    public void mkdirs(String path);
}
```

### FileManager 单例（@Deprecated）

```java
@Deprecated
public class FileManager implements FileExecutorIsEmptyCallback {
    public static FileManager getInstance();
    
    // 读文件回调签名已更新为 3 参数
    public interface ReadFileCallback<T> {
        void callback(@NonNull String path, boolean result, @Nullable T content);
    }
    
    public void read(@NonNull String path, @NonNull ReadFileCallback<String> callback);
    public void write(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback callback);
    public void append(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback callback);
    public void delete(@NonNull String path);
}
```

### KVProvider 类

```java
public class KVProvider implements Provider {
    public final Map<String, MapStorage> mapStorage;  // 存储名称 → MapStorage 映射
}
```

### SimpleKV 类

```java
public class SimpleKV implements MapStorage, LifecycleObserver {
    // MapStorage 接口方法
    public void put(String key, String value);
    public String get(String key, String defaultValue);
    public void remove(String key);
    public boolean contains(String key);
    
    // 类型安全读取
    public int getInt(String key, int defaultValue);
    public long getLong(String key, long defaultValue);
    public float getFloat(String key, float defaultValue);
    public String[] getArray(String key);
    public List<String> getList(String key);
    public Set<String> getSet(String key);
    public Map<String, String> getMap(String key);
}
```

### MapStorage 接口

```java
public interface MapStorage {
    void put(String key, String value);
    String get(String key, String defaultValue);
    void remove(String key);
    void removeAll();
    boolean contains(String key);
}
```

## 文件执行器 API

### FileExecutor

```java
public class FileExecutor {
    // 任务接口
    @FunctionalInterface
    public interface FileTask {
        void run(String path);
    }
    
    // 任务回调
    @FunctionalInterface
    public interface FileTaskCallback {
        void callback(FileExecutor executor, FileTask task, boolean result);
    }
    
    // 共享模式（多个读取可并发）
    public void share(FileTask task);
    public void share(FileTask task, @Nullable FileTaskCallback callback);
    
    // 互斥模式（写入/删除独占）
    public void mutex(FileTask task);
    public void mutex(FileTask task, @Nullable FileTaskCallback callback);
    
    // 移动文件
    public void move(String newPath, @Nullable MoveCallback callback);
    
    // 状态查询
    public boolean taskEmpty();
    public boolean isClosed();
    
    // 关闭
    public void close();
}
```

> **内部实现**: FileExecutor 使用 NIO.2 `FileLock` 实现跨进程文件锁，底层通过 `RandomAccessFile` 和 `FileChannel` 管理文件句柄。

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| FileManagerV2 | `FileManagerV2.getInstance()` 单例 | 进程级，随 JVM 存活 |
| FileManager | `FileManager.getInstance()` 单例（@Deprecated） | 进程级，随 JVM 存活 |
| KVProvider | 直接 new 或 ProviderManager 管理 | 由使用者管理 |
| SimpleKV | 通过 KVProvider 管理 | 由使用者管理 |

## 异常类

| 异常类 | 触发条件 |
|--------|---------|
| `ParentFileException` | 父目录创建失败 |
| `CreateFileException` | 文件创建失败 |
| `NotExistsPathException` | 路径不存在，继承 RuntimeException |
| `LockManagerException` | 文件锁管理异常 |
