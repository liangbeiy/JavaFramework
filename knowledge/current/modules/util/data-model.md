# Util 模块 数据模型

## 核心数据结构

### CachePool 内部状态

```java
public final class CachePool<K, M> {
    // 对象池（Node 复用）
    private final Object nodePoolLock = new Object();
    private final Queue<Node<K, M>> nodePool;  // LinkedList
    
    // 搜索映射
    private final Map<K, Node<K, M>> searchMap;  // HashMap
    
    // LRU 双向环形链表
    private final Node<K, M> head;  // 哨兵节点
    // head.prevNode = 尾节点，head.nextNode = 头节点
    
    // 删除监听器
    private final Object listenersLock = new Object();
    private final Set<ItemHasDeleted<K, M>> listeners;
    
    // 异步工作队列
    private final DispatcherQueue worker;
}
```

### CachePool.Node（链表节点）

```java
private static class Node<K, M> {
    private K key;              // 缓存键
    private M model;            // 缓存值
    private Node<K, M> prevNode; // 前驱节点
    private Node<K, M> nextNode; // 后继节点
}
```

### Logger 内部状态

```java
public final class Logger {
    private static final String LOGGER_GAP = "\t";
    
    private final Object levelLock = new Object();
    private Level level = Level.DEBUG;  // 默认 DEBUG 级别
    
    private final Object handlerLock = new Object();
    private final Set<Handler> handlers;  // 线程安全集合
    
    private final Object printerLock = new Object();
    private Handler printer;  // PrintHandler 实例
}
```

### Logger.PrintHandler 内部实现

```java
public static class PrintHandler implements Handler {
    private static final String DEFAULT_PUCKER = "    ";
    
    // 生成日志字符串
    public static String generateLog(Level level, String tag, String message, Throwable throwable) {
        // 格式：时间 + \t + LEVEL + \t + tag + \t + message + \n + 堆栈跟踪
    }
    
    // 打印到控制台
    public void print(String log);
    
    // 格式化异常堆栈（支持 cause、suppressed）
    private static String formatStackTrace(String prefixString, Throwable throwable);
}
```

### JsonUtil 内部状态

```java
public final class JsonUtil {
    private static final Gson INSTANCE = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
        .create();
}
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| CachePool.Node | 对象池（Queue） | 节点删除后回收到 nodePool，新节点优先从池获取 |
| CachePool.searchMap | HashMap | O(1) 查找，键→节点映射 |
| CachePool LRU | 双向环形链表 | 最近访问的节点在 head 后，最久未访问在 head 前 |
| Logger.handlers | HashSet（同步锁保护） | 支持动态添加/移除处理器 |
| Logger.level | volatile + synchronized | 日志级别，支持动态调整 |
| JsonUtil.Gson | 静态单例 | Gson 实例，注册 LocalDateTime 适配器 |

## 数据流向

```
【CachePool 数据流】
put(key, model) → worker.async() → searchMap.put(key, node) → insert(head, node)
                                      ↓
                              LRU 更新：最近使用在 head 后

get(key, callback) → worker.async() → searchMap.get(key) → remove(node) → insert(head, node)
                                      ↓
                              callback.callback(pool, key, node.model)

remove(key) → worker.async() → searchMap.remove(key) → remove(node) → recycle(node)
                                      ↓
                              通知 listeners → hasRemoved(pool, key, model)

【Logger 数据流】
log(level, tag, message, throwable) → 级别检查 → 遍历 handlers
                                      ↓
                              handler.handle(level, tag, message, throwable)
                                      ↓
                              PrintHandler: 生成日志 → System.out.print()
                              文件Handler: 生成日志 → FileManager.append()
```

## 时间格式化模式

| 常量 | 格式 | 示例 |
|------|------|------|
| FORMAT_ISO | yyyy-MM-dd'T'HH:mm:ss'Z' | 2025-01-01T10:00:00Z |
| FORMAT_COMMON | yyyy-MM-dd HH:mm:ss.SSS | 2025-01-01 10:00:00.123 |
| FORMAT_SIMPLE | yyyy/MM/dd HH:mm:ss | 2025/01/01 10:00:00 |
| FORMAT_DATETIME_SECOND | yyyy-MM-dd HH:mm:ss | 2025-01-01 10:00:00 |
| FORMAT_DATETIME | yyyy-MM-dd HH:mm | 2025-01-01 10:00 |
| FORMAT_DATE | yyyy-MM-dd | 2025-01-01 |
