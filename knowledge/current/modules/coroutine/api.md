# Coroutine 模块 API 文档

## 对外暴露接口

### DispatchQueue 接口

```java
package com.cxuy.framework.coroutine;

public interface DispatchQueue {
    // 同步执行
    void sync(@NonNull Task task);
    void sync(long delay, @NonNull Task task);
    
    // 异步执行
    void async(@NonNull Task task);
    void async(long delay, @NonNull Task task);
    
    // 移除任务
    void remove(@NonNull Task task);
    
    // 关闭
    void shutdown();
    void awaitShutdown();
    void shutdown(boolean await, boolean isClear);
    
    // 状态观察者
    void addObserver(@Nullable StatusObserver observer);
    void removeObserver(@Nullable StatusObserver observer);
    
    // 状态枚举
    enum Status { INIT(-1), BUSY(0), IDLE(1), DORMANT(2), DESTROY(3) }
    
    // 状态观察者接口
    interface StatusObserver {
        void onChanged(DispatcherQueue queue, Status status);
    }
}
```

> **注意**: `standard`/`io`/`once` 静态字段已从 DispatchQueue 接口移至 `Coroutines` 类。

### Coroutines 类

```java
package com.cxuy.framework.coroutine;

public final class Coroutines {
    // 全局调度器实例
    public static final DispatchQueue standard;  // 标准调度队列，单线程执行
    public static final DispatchQueue io;        // IO 调度队列，协程模式，线程池并发
    
    // 获取默认实例
    public static Coroutines getDefault();
    
    // 自定义协程队列管理
    public DispatchQueue getCoroutine(String name);
    public <T extends DispatchQueue> T getCoroutine(String queueName, Class<T> clazz);
    public void addCoroutine(String name, DispatchQueue queue);
    public void removeCoroutine(String name);
}
```

### OnceDispatcherQueue（@Deprecated since 1.31.5.0）

```java
public final class OnceDispatcherQueue extends DispatcherQueue {
    public static OnceDispatcherQueue get();
    public void once(@NonNull String token, @NonNull Task task);
    public static void once(long delay, @NonNull String token, @NonNull Task task);
}
```

> **弃用提示**: 请使用 `Coroutines.standard` 或自定义队列替代。

### DispatchGroup 类

```java
public class DispatchGroup {
    public DispatchGroup();           // 默认 autoLeave=true
    public DispatchGroup(boolean autoLeave);
    
    // 添加异步任务（BUILDING 状态）
    public void async(DispatchQueue queue, Task task);
    
    // 提交任务组并设置完成回调（转为 COMMITTED 状态）
    public void notify(DispatchQueue queue, Task task);
    
    // 手动标记任务完成（autoLeave=false 时使用）
    public void leave();
}
```

### Task 接口

```java
package com.cxuy.framework.coroutine.task;

public interface Task extends Runnable { }
```

### IDLETask 接口

```java
public interface IDLETask extends Task {
    boolean reuse();  // 返回 true 则任务执行后重新入队
}
```

## 组件注册表

| 组件 | 访问方式 | 说明 |
|------|---------|------|
| `Coroutines.standard` | 静态字段 | 标准调度队列，单线程执行 |
| `Coroutines.io` | 静态字段 | IO 调度队列，协程模式，线程池并发 |
| `Coroutines.once` | 静态字段 | 单次执行队列（@Deprecated） |
| `Coroutines.getDefault()` | 静态方法 | 获取 Coroutines 单例 |
| `TaskItemManager` | `TaskItemManager.get()` | 任务项对象池，内部单例 |

## 内部 API（包可见）

### DispatcherQueue 扩展方法

```java
public class DispatcherQueue implements DispatchQueue {
    // 构造方法
    public DispatcherQueue();
    public DispatcherQueue(boolean isCoroutine);
    public DispatcherQueue(String name);
    public DispatcherQueue(String name, boolean isCoroutine);
    
    // 内部状态查询
    public Status getStatus();
    
    // 内部任务处理（protected）
    protected void handleTask(Task task);
    
    // 内部关闭方法
    public void shutdown(boolean await, boolean clear);
}
```

## 异常类

| 异常类 | 触发条件 |
|--------|---------|
| `DispatcherQueueHasDestroyedException` | 向已销毁的队列提交任务 |
| `NeedDestroyDispatcherQueueException` | 工作线程检测到队列应销毁 |
| `GroupHasCommittedException` | 任务组已提交后再次调用 notify |
| `GroupHasDoneException` | 任务组已完成后调用 leave |
| `GroupStatusErrorException` | 任务组状态错误 |
