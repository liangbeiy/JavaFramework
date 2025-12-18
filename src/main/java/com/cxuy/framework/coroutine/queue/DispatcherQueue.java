/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.queue;

import com.cxuy.framework.coroutine.Bundle;
import com.cxuy.framework.coroutine.DispatchContext;
import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.coroutine.exception.DispatcherQueueHasDestroyedException;
import com.cxuy.framework.coroutine.exception.NeedDestroyDispatcherQueueException;
import com.cxuy.framework.coroutine.task.IDLETask;
import com.cxuy.framework.coroutine.task.Task;
import com.cxuy.framework.coroutine.task.TaskItem;
import com.cxuy.framework.coroutine.task.TaskItemManager;
import com.cxuy.framework.util.TimeUtil;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public class DispatcherQueue implements DispatchQueue {
    // CPU核心数量
    private static final int CPU_CORE = Runtime.getRuntime().availableProcessors();

    // 自定义线程池
    // private static final Object REF_POOL_LOCK = new Object();
    // private static int coroutinePoolRefCount = 0;

    private static final String DEFAULT_DISPATCHER_NAME = "default_DispatcherQueue#Name";

    protected final String name;

    protected final boolean isCoroutine;
    private final ThreadPoolExecutor coroutinePool;

    protected final Object statusLock = new Object();
    protected volatile Status status;

    protected final Object statusObserversLock = new Object();
    protected Set<StatusObserver> statusObservers = new HashSet<>();

    private final Object threadLock = new Object();
    protected Thread thread;

    private final Map<Task, DispatchContext> contextMap = new HashMap<>();

    private final Object taskQueueLock = new Object();
    protected final PriorityQueue<TaskItem> taskQueue = new PriorityQueue<>();
    protected volatile long lastIDLETimestamp;
    protected final PriorityQueue<TaskItem> idleQueue = new PriorityQueue<>();
    protected PriorityQueue<TaskItem> queue;

    protected long submitTaskId = 0;

    public DispatcherQueue() {
        this(DEFAULT_DISPATCHER_NAME, false);
    }

    public DispatcherQueue(boolean isCoroutine) {
        this(DEFAULT_DISPATCHER_NAME, isCoroutine);
    }

    public DispatcherQueue(String name) {
        this(name, false);
    }

    public DispatcherQueue(String name, boolean isCoroutine) {
        this.name = name;
        this.isCoroutine = isCoroutine;
        setStatus(Status.INIT);
        if(isCoroutine) {
            int core = Math.max(CPU_CORE / 2, 1);
            int maxCore = Math.max(CPU_CORE * 2, 2);
            coroutinePool = new ThreadPoolExecutor(
                    core, // 核心线程数
                    maxCore, // 最大线程数
                    30, // 非核心线程存活时间（秒）
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(128), // 有界队列
                    Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.CallerRunsPolicy());  // 拒绝策略
        } else {
            coroutinePool = null;
        }
    }

    @Override
    public void sync(Task task) {
        sync(0, null, task);
    }

    @Override
    public void sync(Bundle bundle, Task task) {
        sync(0, bundle, task);
    }

    @Override
    public void sync(long delay, Task task) {
        sync(delay, null, task);
    }

    @Override
    public void sync(long delay, Bundle bundle, Task task) {
        CountDownLatch latch = new CountDownLatch(1);
        async(delay, bundle, (context) -> {
            try {
                task.run(context);
            }
            finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public DispatchContext async(Task task) {
        return async(0, null, task);
    }

    @Override
    public DispatchContext async(long delay, Task task) {
        return async(delay, null, task);
    }

    @Override
    public DispatchContext async(Bundle bundle, Task task) {
        return async(0, bundle, task);
    }

    @Override
    public DispatchContext async(long delay, Bundle bundle, Task task) throws DispatcherQueueHasDestroyedException {
        if(status == Status.DESTROY) {
            throw new DispatcherQueueHasDestroyedException(name);
        }
        if(task == null) {
            throw new NullPointerException("task not allow nullable");
        }
        boolean containContext = contextMap.containsKey(task);
        if(containContext) {
            return contextMap.get(task);
        }
        DispatchContext context = new DispatchContext(submitTaskId, this);
        context.setBundle(bundle);
        contextMap.put(task, context);
        TaskItem item = TaskItemManager.get().acquire(delay, submitTaskId, task);
        synchronized(taskQueueLock) {
            if (status == Status.DESTROY) {
                TaskItemManager.get().recycle(item); // 回收任务项
                throw new DispatcherQueueHasDestroyedException(name);
            }
            if(task instanceof IDLETask) {
                idleQueue.offer(item);
            }
            else {
                queue = taskQueue;
                taskQueue.offer(item);
            }
            submitTaskId++;
            taskQueueLock.notifyAll();
        }
        createThreadIfNeed();
        return context;
    }

    @Override
    public DispatchContext addIdle(IDLETask idle) {
        return addIdle(0, idle);
    }

    @Override
    public DispatchContext addIdle(long delay, IDLETask idle) {
        return addIdle(delay, null, idle);
    }

    @Override
    public DispatchContext addIdle(Bundle bundle, IDLETask idle) {
        return async(0, bundle, idle);
    }

    @Override
    public DispatchContext addIdle(long delay, Bundle bundle, IDLETask idle) {
        return async(delay, bundle, idle);
    }

    public void awaitShutdown() {
        shutdown(true, true);
    }

    public void shutdown() {
        shutdown(false, false);
    }

    public Status getStatus() {
        Status status;
        synchronized(statusLock) {
            status = this.status;
        }
        return status;
    }

    @Override
    public void addObserver(StatusObserver observer) {
        if(observer == null) {
            return;
        }
        synchronized(statusObserversLock) {
            statusObservers.add(observer);
        }
    }

    @Override
    public void removeObserver(StatusObserver observer) {
        if(observer == null) {
            return;
        }
        synchronized(statusObserversLock) {
            statusObservers.remove(observer);
        }
    }

    private void setStatus(Status status) {
        synchronized(statusLock) {
            this.status = status;
        }
        synchronized(statusObserversLock) {
            for(StatusObserver observer : statusObservers) {
                observer.onChanged(this, status);
            }
        }
    }

    protected void handleTask(DispatchContext context, Task task) {
        if(task == null) {
            return;
        }
        if(!isCoroutine || task instanceof IDLETask) {
            task.run(context);
            if(task instanceof IDLETask && ((IDLETask)task).reuse()) {
                TaskItem item = TaskItemManager.get().acquire(0, submitTaskId, task);
                synchronized(taskQueueLock) {
                    taskQueue.offer(item);
                }
                return;
            }
            contextMap.remove(task);
            return;
        }
        // is coroutine, pool not be null
        if(coroutinePool == null) {
            throw new IllegalStateException("coroutinePool is null");
        }
        if(coroutinePool.isShutdown()) { // 意外情况，降级处理
            task.run(context);
            return;
        }
        try {
            coroutinePool.submit(() -> {
                task.run(context);
            });
        }
        catch(RejectedExecutionException e) { // 降级处理 直接执行
            task.run(context);
        }
    }

    public void shutdown(boolean await, boolean clear) {
        if(status.rawValue < Status.DESTROY.rawValue) {
            synchronized(statusLock) {
                setStatus(Status.DESTROY);
            }
            synchronized(taskQueueLock) {
                if(clear) {
                    for (TaskItem each : taskQueue) {
                        TaskItemManager.get().recycle(each);
                    }
                    taskQueue.clear();
                }
                taskQueueLock.notifyAll();
            }
            if(await) {
                synchronized(threadLock) {
                    try {
                        thread.join();
                    }
                    catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if(isCoroutine) {
                shutdownPool(await);
            }
        }
    }

    private void shutdownPool(boolean await) {
        // 开始关闭POOL
        coroutinePool.shutdown();
        if (await) {
            try {
                if (!coroutinePool.awaitTermination(30, TimeUnit.SECONDS)) {
                    coroutinePool.shutdownNow();
                }
            } catch (InterruptedException e) {
                coroutinePool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void createThreadIfNeed() {
        synchronized(threadLock) {
            if(thread == null) {
                thread = new T(name, new DispatcherQueueWeakRef(this));
                thread.setDaemon(false);
                thread.start();
            }
        }
        setStatus(Status.BUSY);
        synchronized(taskQueueLock) {
            queue = taskQueue;
        }
    }

    private static class T extends Thread {
        private final DispatcherQueueWeakRef ref;
        public T(String name, DispatcherQueueWeakRef ref) {
            super(name + "#Worker");
            this.ref = ref;
        }

        @Override
        public void run() {
            while(true) {
                DispatcherQueue queue = this.ref.get();
                if(queue == null || queue.status == Status.DESTROY) {
                    return;
                }
                queue = null;
                Task needExecute;
                try {
                    needExecute = fetchTask(this.ref);
                    queue = this.ref.get();
                    if(queue != null && needExecute != null) {
                        DispatchContext context = queue.contextMap.get(needExecute);
                        if(!context.isCancel()) {
                            queue.handleTask(context, needExecute);
                        }
                    }
                }
                catch (Exception e) {
                    if(e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    queue = this.ref.get();
                    if(queue != null) {
                        synchronized(queue.threadLock) {
                            queue.thread = null;
                        }
                        synchronized(queue.statusLock) {
                            queue.setStatus(Status.DORMANT);
                        }
                        if(queue.isCoroutine) {
                            queue.shutdownPool(false);
                        }
                    }
                    return;
                }
            }
        }

        private Task fetchTask(DispatcherQueueWeakRef ref) throws NeedDestroyDispatcherQueueException, InterruptedException {
            DispatcherQueue q = ref.get();
            if(q == null || q.status == Status.DESTROY) {
                return null;
            }
            final Object queueLock = q.taskQueueLock;
            synchronized(queueLock) {
                if(q.queue.isEmpty() && q.queue != q.idleQueue) { // 尝试切换至idle执行
                    synchronized(q.statusLock) {
                        q.setStatus(Status.IDLE);
                    }
                    q.lastIDLETimestamp = TimeUtil.now();
                    q.queue = q.idleQueue;
                    if(q.queue.isEmpty()) { // 如果还是没有任务 应该结束掉当前线程
                        throw new NeedDestroyDispatcherQueueException(q.name);
                    }
                }
                TaskItem item = q.queue.poll();
                if(item == null) {
                    return null;
                }
                long current = System.currentTimeMillis();
                long itemRunMills = item.getTask() instanceof IDLETask ? item.getDelay() + q.lastIDLETimestamp : item.getRunMills();
                if(itemRunMills <= current) {
                    Task needExecute = item.getTask();
                    TaskItemManager.get().recycle(item);
                    return needExecute;
                }
                else {
                    q.queue.offer(item);
                    long delayMillis = itemRunMills - current;
                    while (delayMillis > 0 && q.status != Status.DESTROY) {
                        q = null;
                        queueLock.wait(delayMillis);
                        q = ref.get();
                        if(q == null || !q.taskQueue.isEmpty()) {
                            return null;
                        }
                        delayMillis = itemRunMills - System.currentTimeMillis();
                    }
                    return null;
                }
            }
        }
    }

    private static class DispatcherQueueWeakRef extends WeakReference<DispatcherQueue> {
        public DispatcherQueueWeakRef(DispatcherQueue referent) {
            super(referent);
        }
    }
}