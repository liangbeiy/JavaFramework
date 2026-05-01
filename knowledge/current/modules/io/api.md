# IO 模块 API 文档

## 对外暴露接口

### FileProvider 接口

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

### FileManager 单例

```java
public class FileManager implements FileExecutorIsEmptyCallback {
    public static FileManager getInstance();  // 单例获取
    
    // 文件状态
    public boolean isExist(@NonNull String path);
    public String getAbsolutePath();  // 获取当前工作目录
    
    // 文件创建
    public void createFile(String path) throws ParentFileException, CreateFileException;
    
    // 文件读取（异步回调）
    public void read(@NonNull String path, @NonNull ReadFileCallback<String> callback);
    
    // 文件写入
    public void write(@NonNull String path, @Nullable String content);
    public void write(@NonNull String path, @Nullable String content, 
                      @Nullable WriteFileCallback<String> callback);
    
    // 文件追加
    public void append(@NonNull String path, @Nullable String content);
    public void append(@NonNull String path, @Nullable String content,
                      @Nullable WriteFileCallback<String> callback);
    
    // 文件删除
    public void delete(@NonNull String path);
}
```

### FileManager 回调接口

```java
// 读文件回调
@FunctionalInterface
public interface ReadFileCallback<T> {
    void callback(@NonNull String path, @Nullable T result);
}

// 写文件回调
@FunctionalInterface
public interface WriteFileCallback<T> {
    int MODE_WRITE = 0;
    int MODE_APPEND = 1;
    void callback(@NonNull String path, @Nullable T content, int mode, boolean result);
}
```

### KVProvider 类

```java
public class KVProvider implements Provider {
    public final Map<String, MapStorage> mapStorage;  // 存储名称 → MapStorage 映射
}
```

### KVMap 类

```java
public class KVMap implements MapStorage {
    public static final int MULTI_PROCESS = 0x0001;  // 多进程模式标志
    
    // 构造（内部使用，需通过 KVProvider 管理）
    private KVMap(@Nullable Context context, String name, int mode);
    
    // MapStorage 接口方法
    public void put(String key, String value);
    public String get(String key, String defaultValue);
    public void remove(String key);
    public void removeAll();
    public boolean contains(String key);
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

## 文件执行器 API（内部）

### FileExecutor

```java
public class FileExecutor {
    // 共享模式（多个读取可并发）
    public void share(Runnable task);
    
    // 互斥模式（写入/删除独占）
    public void mutex(Runnable task);
}
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| FileManager | `FileManager.getInstance()` 单例 | 进程级，随 JVM 存活 |
| KVProvider | 直接 new 或 ProviderManager 管理 | 由使用者管理 |
| ReadFileProvider | 直接 new | 由使用者管理 |
| WriteFileProvider | 直接 new | 由使用者管理 |
| ExtraFileProvider | 直接 new | 由使用者管理 |

## 异常类

| 异常类 | 触发条件 |
|--------|---------|
| `ParentFileException` | 父目录创建失败 |
| `CreateFileException` | 文件创建失败 |
| `NotExistsPathException` | 路径不存在 |
| `LockManagerException` | 文件锁管理异常 |
