/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.queue;

import com.cxuy.framework.annotation.Nullable;
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

    private static final String NAME_DISPATCHER_QUEUE_DEFAULT = "com.cxuy.framework.coroutine#default_name";

    protected final String name;

    protected final boolean isCoroutine;
    private final ThreadPoolExecutor coroutinePool;

    protected final Object statusLock = new Object();
    protected volatile Status status;

    protected final Object statusObserversLock = new Object();
    protected Set<StatusObserver> statusObservers = new HashSet<>();

    private final Object threadLock = new Object();
    protected Thread thread;

    private final Object taskQueueLock = new Object();
    protected Map<Task, TaskItem> taskItemMap = new HashMap<>();
    protected final PriorityQueue<TaskItem> taskQueue = new PriorityQueue<>();
    protected volatile long lastIDLETimestamp;
    protected final PriorityQueue<TaskItem> idleQueue = new PriorityQueue<>();
    protected PriorityQueue<TaskItem> queue;

    public DispatcherQueue() {
        this(NAME_DISPATCHER_QUEUE_DEFAULT, false);
    }

    public DispatcherQueue(boolean isCoroutine) {
        this(NAME_DISPATCHER_QUEUE_DEFAULT, isCoroutine);
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
        sync(0, task);
    }

    @Override
    public void sync(long delay, Task task) {
        CountDownLatch latch = new CountDownLatch(1);
        async(delay, () -> {
            try {
                task.run();
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
    public void async(Task task) {
        async(0, task);
    }

    @Override
    public void async(long delay, Task task) throws DispatcherQueueHasDestroyedException {
        if(status == Status.DESTROY) {
            throw new DispatcherQueueHasDestroyedException(name);
        }
        if(task == null) {
            throw new NullPointerException("task not allow nullable");
        }
        TaskItem item = TaskItemManager.get().create(delay, task);
        if (status == Status.DESTROY) {
            TaskItemManager.get().recycle(item); // 回收任务项
            throw new DispatcherQueueHasDestroyedException(name);
        }
        synchronized(taskQueueLock) {
            if(taskItemMap.containsKey(task)) {
                remove(task);
            }
            taskItemMap.put(task, item);
            if(task instanceof IDLETask) {
                idleQueue.offer(item);
            }
            else {
                queue = taskQueue;
                taskQueue.offer(item);
            }
            taskQueueLock.notifyAll();
        }
        createThreadIfNeed();
        switchWorkQueueIfNeed();
        synchronized(taskQueueLock) {
            setStatus(taskQueue.isEmpty() ? Status.IDLE : Status.BUSY);
            synchronized(taskQueueLock) {
                queue = taskQueue;
            }
        }
    }

    @Override
    public void remove(Task task) {
        if(task == null) {
            return;
        }
        synchronized (taskQueueLock) {
            TaskItem taskItem = taskItemMap.remove(task);
            if(taskItem != null) {
                taskQueue.remove(taskItem);
                idleQueue.remove(taskItem);
            }
        }
        switchWorkQueueIfNeed();
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

    protected void handleTask(Task task) {
        if(task == null) {
            return;
        }
        if(!isCoroutine || task instanceof IDLETask) {
            task.run();
            if(task instanceof IDLETask && ((IDLETask)task).reuse()) {
                TaskItem item = TaskItemManager.get().create(0, task);
                synchronized(taskQueueLock) {
                    taskQueue.offer(item);
                }
                return;
            }
            return;
        }
        // is coroutine, pool not be null
        if(coroutinePool == null) {
            throw new IllegalStateException("coroutinePool is null");
        }
        if(coroutinePool.isShutdown()) { // 意外情况，降级处理
            task.run();
            return;
        }
        try {
            coroutinePool.submit(task);
        }
        catch(RejectedExecutionException e) { // 降级处理 直接执行
            task.run();
        }
    }

    public void shutdown(boolean await, boolean clear) {
        if(status.rawValue >= Status.DESTROY.rawValue) {
            return;
        }
        synchronized(statusLock) {
            this.status = Status.DESTROY;
        }
        synchronized(statusObserversLock) {
            for(StatusObserver observer : statusObservers) {
                observer.onChanged(this, Status.DESTROY);
            }
        }
        synchronized(taskQueueLock) {
            if(clear) {
                for (TaskItem each : taskQueue) {
                    TaskItemManager.get().recycle(each);
                }
                taskQueue.clear();
                for (TaskItem each : idleQueue) {
                    TaskItemManager.get().recycle(each);
                }
                idleQueue.clear();
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
    }

    private void switchWorkQueueIfNeed() {
        synchronized(taskQueueLock) {
            if(!taskQueue.isEmpty()) {
                setStatus(Status.BUSY);
                queue = taskQueue;
            } else {
                setStatus(Status.IDLE);
                if(!idleQueue.isEmpty()) {
                    lastIDLETimestamp = TimeUtil.now();
                    queue = idleQueue;
                    return;
                }
                setStatus(Status.DORMANT);
            }
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
                        queue.handleTask(needExecute);
                    }
                }
                catch (Exception e) {
                    if(e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    closeThreadForDormant(this.ref.get());
                    return;
                }
            }
        }

        private Task fetchTask(DispatcherQueueWeakRef ref) throws NeedDestroyDispatcherQueueException, InterruptedException {
            DispatcherQueue q = ref.get();
            if(q == null || q.status == Status.DESTROY) {
                return null;
            }
            q.switchWorkQueueIfNeed();
            synchronized(q.statusLock) {
                if(q.getStatus() == Status.DORMANT) {
                    throw new NeedDestroyDispatcherQueueException(q.name);
                }
            }
            final Object queueLock = q.taskQueueLock;
            synchronized(queueLock) {
                TaskItem item = q.queue.poll();
                if(item == null) {
                    return null;
                }
                long current = System.currentTimeMillis();
                long itemRunMills = item.getTask() instanceof IDLETask ? item.getDelay() + q.lastIDLETimestamp : item.getRunMills();
                if(itemRunMills <= current) {
                    Task needExecute = item.getTask();
                    q.taskItemMap.remove(needExecute);
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

        private void closeThreadForDormant(@Nullable DispatcherQueue queue) {
            if(queue == null) {
                return;
            }
            synchronized(queue.threadLock) {
                queue.thread = null;
            }
            if(queue.isCoroutine) {
                queue.shutdownPool(false);
            }
        }
    }

    private static class DispatcherQueueWeakRef extends WeakReference<DispatcherQueue> {
        public DispatcherQueueWeakRef(DispatcherQueue referent) {
            super(referent);
        }
    }
}