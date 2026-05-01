# Coroutine 模块 数据模型

## 核心数据结构

### TaskItem（任务项）

```java
// 位置：com.cxuy.framework.coroutine.task.TaskItem
public class TaskItem implements Comparable<TaskItem> {
    private long id;              // 唯一标识，用于优先级比较
    private long runMills;        // 计划执行时间戳（System.currentTimeMillis() + delay）
    private Task task;            // 实际任务
    
    public long getRunMills();
    public long getDelay();       // 返回 runMills - System.currentTimeMillis()
    public Task getTask();
}
```

### TaskItemManager（对象池）

```java
// 位置：com.cxuy.framework.coroutine.task.TaskItemManager
public class TaskItemManager {
    private static final TaskItemManager INSTANCE = new TaskItemManager();
    
    private final Queue<TaskItem> pool;  // 对象池队列
    
    public static TaskItemManager get();
    public TaskItem create(long delay, Task task);  // 从池获取或新建
    public void recycle(TaskItem item);             // 回收至池
}
```

### DispatcherQueue 内部状态

```java
public class DispatcherQueue implements DispatchQueue {
    protected final String name;
    protected final boolean isCoroutine;
    private final ThreadPoolExecutor coroutinePool;  // 协程模式线程池
    
    // 状态相关
    protected volatile Status status;
    protected final Object statusLock = new Object();
    protected final Set<StatusObserver> statusObservers;
    
    // 任务队列
    protected final Object taskQueueLock = new Object();
    protected final Map<Task, TaskItem> taskItemMap;       // 任务→任务项映射
    protected final PriorityQueue<TaskItem> taskQueue;     // 主任务队列（按执行时间排序）
    protected final PriorityQueue<TaskItem> idleQueue;     // 空闲任务队列
    protected volatile long lastIDLETimestamp;             // 上次空闲时间
    protected PriorityQueue<TaskItem> queue;               // 当前工作队列
    
    // 工作线程
    protected Thread thread;
    private final Object threadLock = new Object();
}
```

### DispatchGroup 内部状态

```java
public class DispatchGroup {
    private final boolean autoLeave;
    private Status status;  // BUILDING -> COMMITTED -> DONE
    
    private Transaction.Builder transactionBuilder;
    private final Semaphore semaphore;
    
    // 状态枚举
    private enum Status { BUILDING, COMMITTED, DONE }
    
    // 事务（不可变）
    private static final class Transaction {
        public final Map<Task, DispatchQueue> tasks;  // 任务→执行队列映射
        public final DispatchQueue notifyQueue;        // 完成回调执行队列
        public final Task notifyTask;                  // 完成回调任务
    }
}
```

## 线程池配置（协程模式）

```java
// DispatcherQueue 构造函数中初始化
if(isCoroutine) {
    int core = Math.max(CPU_CORE / 2, 1);
    int maxCore = Math.max(CPU_CORE * 2, 2);
    coroutinePool = new ThreadPoolExecutor(
        core,                      // 核心线程数
        maxCore,                   // 最大线程数
        30,                        // 非核心线程存活时间（秒）
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(128),  // 有界队列，容量128
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
    );
}
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| TaskItem | 对象池（Queue） | `TaskItemManager` 管理，避免频繁创建/回收 |
| DispatcherQueue.standard | 静态字段 | 全局唯一标准队列 |
| DispatcherQueue.io | 静态字段 | 全局唯一 IO 协程队列 |
| OnceDispatcherQueue | 静态单例 | 全局唯一单次执行队列 |
| statusObservers | HashSet | 状态观察者集合，支持动态添加/移除 |

## 数据流向

```
【任务提交流】
async(delay, task) → TaskItemManager.create() → TaskItem
                                      ↓
                            taskItemMap.put(task, item)
                            taskQueue.offer(item)
                                      ↓
                            createThreadIfNeed() → 唤醒工作线程
                                      ↓
                            switchWorkQueueIfNeed() → 更新状态 BUSY

【任务执行流】
工作线程 fetchTask() → taskQueue.poll() → TaskItem
                                      ↓
                            检查延迟 → wait(delay) 或立即执行
                                      ↓
                            taskItemMap.remove(task)
                            TaskItemManager.recycle(item)
                                      ↓
                            handleTask(task) → task.run()
                                      ↓
                            更新状态（BUSY → IDLE → DORMANT）

【协程模式流】
handleTask(task) → coroutinePool.submit(task) → 线程池执行
                                      ↓
                            降级：若池关闭或拒绝，直接 task.run()
```
