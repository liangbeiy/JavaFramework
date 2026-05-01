# Coroutine 模块 业务流程

## 任务调度流程

```
┌─────────────────────────────────────────────────────────────┐
│                  DispatcherQueue 任务调度                      │
└─────────────────────────────────────────────────────────────┘

  提交任务               工作线程                  任务执行
     │                        │                         │
     ▼                        ▼                         ▼
┌─────────┐          ┌──────────────┐          ┌──────────┐
│ async() │──sync──▶│ taskQueue   │──poll──▶│fetchTask │
│ delay,  │         │ PriorityQueue│        │          │
│ task    │         └──────┬───────┘        └────┬─────┘
     │                    │                      │
     │                    │ (delay>0)           │
     │                    ▼                      │
     │              ┌──────────────┐            │
     │              │ wait(delay)  │            │
     │              └──────┬───────┘            │
     │                    │                      │
     │                    ▼                      ▼
     │              ┌──────────────┐      ┌──────────┐
     │              │handleTask()  │─────▶│task.run() │
     │              └──────────────┘      └──────────┘
     │                                               │
     │                   ┌──────────────┐             │
     └──────────────────│status=BUSY   │◀────────────┘
                        └──────────────┘
```

### 任务提交流程

1. 调用 `async(delay, task)`，检查队列状态（已销毁则抛异常）
2. 通过 `TaskItemManager.get().create(delay, task)` 创建任务项
3. 若任务已存在则先移除旧任务，再将新任务项放入 `taskQueue`（或 `idleQueue`）
4. 调用 `createThreadIfNeed()` 确保工作线程运行
5. 调用 `switchWorkQueueIfNeed()` 切换工作队列，更新状态

### 工作线程执行流程

1. 工作线程循环调用 `fetchTask(ref)` 获取待执行任务
2. 从当前工作队列（`taskQueue` 或 `idleQueue`）poll 任务项
3. 若任务有延迟，计算等待时间并 `wait(delayMillis)`
4. 获取任务后，从 `taskItemMap` 移除，回收 TaskItem 到对象池
5. 调用 `handleTask(task)` 执行：
   - 非协程模式或 IDLETask：直接 `task.run()`
   - 协程模式：提交到 `coroutinePool` 线程池
   - IDLETask 且 `reuse()=true`：重新入队

### 队列状态转换

```
INIT → BUSY（有任务入队）→ IDLE（任务队列空）→ DORMANT（空闲队列也空）→ DESTROY（shutdown）
```

## DispatchGroup 任务组流程

```
┌─────────────────────────────────────────────────────────────┐
│                    DispatchGroup 编排                         │
└─────────────────────────────────────────────────────────────┘

  构建阶段                提交阶段               完成阶段
 (BUILDING)            (COMMITTED)            (DONE)
     │                        │                      │
     ▼                        ▼                      ▼
┌─────────┐            ┌──────────────┐      ┌──────────┐
│async()  │──────────▶│Transaction   │      │Semaphore │
│添加任务  │           │.Builder      │      │.acquire()│
└─────────┘            │ 收集 tasks   │      └────┬─────┘
                        └──────┬───────┘           │
                               │                   │
                               ▼                   ▼
                        ┌──────────────┐     ┌──────────┐
                        │notify()      │────▶│所有任务   │
                        │设置notifyTask│     │执行完成   │
                        └──────────────┘     └────┬─────┘
                                                  │
                                                  ▼
                                           ┌──────────┐
                                           │notifyTask│
                                           │在notifyQueue执行│
                                           └──────────┘
```

### 任务组使用流程

1. 创建 `DispatchGroup`（可指定 `autoLeave=true/false`）
2. 调用 `async(queue, task)` 多次添加任务（状态必须为 BUILDING）
3. 调用 `notify(queue, task)` 提交任务组并设置完成回调（状态变为 COMMITTED）
4. 所有任务在各自队列异步执行，每个完成后 `semaphore.release()`
5. 新线程等待 `semaphore.acquire(tasks.size())`
6. 全部完成后，在 `notifyQueue` 执行 `notifyTask`（状态变为 DONE）

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| 向已销毁队列提交任务 | 抛出 `DispatcherQueueHasDestroyedException` |
| 提交 null 任务 | 抛出 `NullPointerException` |
| 重复提交同一任务 | 先移除旧任务，再添加新任务 |
| 任务执行异常 | 异常被捕获并记录，不影响队列继续运行 |
| 协程线程池拒绝执行 | 降级处理：直接 `task.run()` |
| 协程线程池已关闭 | 降级处理：直接 `task.run()` |
| IDLETask reuse=true | 任务执行后重新创建 TaskItem 入队 |
| DispatchGroup 状态错误 | 抛出对应异常（Committed/Done/StatusError） |
| Semaphore.acquire 中断 | 调用 `Thread.currentThread().interrupt()` |
| 工作线程检测到 DORMANT | 抛出 `NeedDestroyDispatcherQueueException`，线程退出 |
