# Util 模块 业务流程

## Logger 日志处理流程

```
┌─────────────────────────────────────────────────────────────┐
│                  Logger 日志处理                               │
└─────────────────────────────────────────────────────────────┘

  调用日志方法             级别检查               处理器链
     │                        │                      │
     ▼                        ▼                      ▼
┌─────────┐            ┌──────────────┐      ┌──────────┐
│Logger.d │──level──▶│检查日志级别  │──pass──▶│遍历      │
│(tag,msg)│          │rawValue >=   │      │handlers  │
└─────────┘          │logger.level │      └────┬─────┘
                      └──────────────┘           │
                                                ▼
                                         ┌──────────────┐
                                         │handler.     │
                                         │handle()     │
                                         └────┬─────────┘
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                  ┌──────────┐        ┌──────────┐       ┌──────────┐
                  │PrintHandler│       │File      │       │Custom    │
                  │(控制台)    │       │Handler   │       │Handler   │
                  └──────────┘        │(文件)    │       └──────────┘
                                        └──────────┘
```

### 日志输出流程

1. 调用 `Logger.d(tag, message)` 或带 throwable 的重载
2. 内部调用 `log(level, tag, message, throwable)`
3. 检查 `level.rawValue >= logger.level.rawValue`（不通过则丢弃）
4. 遍历 `handlers` 集合，调用每个 `handler.handle(level, tag, message, throwable)`
5. `PrintHandler` 生成格式化的日志字符串并输出到控制台
6. 文件 Handler 生成日志字符串并追加到 `./logger/logcat.log`

### 日志格式

```
时间ISO格式    LEVEL    TAG    消息
↓              ↓        ↓       ↓
2025-01-01T10:00:00Z    D    Tag    Message
    at java.lang.Thread.run(Thread.java:1234)
    ... 2 more
```

## CachePool LRU 缓存流程

```
┌─────────────────────────────────────────────────────────────┐
│              CachePool 操作                                   │
└─────────────────────────────────────────────────────────────┘

  put(key, model)        get(key, callback)        remove(key)
        │                        │                        │
        ▼                        ▼                        ▼
  ┌─────────────────────────────────────────────────────┐
  │  worker.async() 异步执行                            │
  │  searchMap: Map<K, Node>                           │
  │  head ←→ node1 ←→ node2 ←→ ... ←→ head (双向环)  │
  └─────────────────────────────────────────────────────┘
        │                        │                        │
        ▼                        ▼                        ▼
  插入到head后           移动到head后            从链表移除
  更新searchMap          回调callback             recycle Node
                              │                 通知listeners
                              ▼
                        回调callback
```

### LRU 策略

- 新插入或访问的节点移动到 head 后面（最近使用）
- 双向环形链表维护顺序
- Node 对象通过 `nodePool` 对象池复用

## JsonUtil 序列化/反序列化流程

```
序列化：object → Gson.toJson() → JSON String
反序列化：JSON String → Gson.fromJson() → object
获取值：JSON String → JsonObject → 按 key 获取 → Gson.fromJson() → value
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| TextUtils.isEmpty(null) | 返回 true |
| TextUtils.equals(null, null) | 返回 true |
| JsonUtil.fromJson 失败 | 捕获 JsonSyntaxException，返回 null |
| JsonUtil.getValue 空 JSON | 返回 defaultValue |
| Logger 级别过滤 | rawValue 小于当前级别则静默丢弃 |
| CachePool key 为 null | put/remove/get 直接返回，不操作 |
| CachePool 异步操作 | 通过 DispatcherQueue.io 执行，不阻塞调用线程 |
| TimeUtil 构造 | 私有构造，抛异常（工具类不能实例化） |
| Logger 文件日志 | 使用 FileManager.append()，失败不影响其他 Handler |
