/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.util.TimeUtil;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public class DispatchQueue {
    public interface Task {
        void run(DispatchContext context);
    }

    public interface StatusObserver {
        void onChanged(DispatchQueue queue, Status status);
    }

    /**
     * 停车任务
     * 在队列空闲时候，会取出这个任务执行
     */
    public interface IDLETask extends Task {
        /**
         * 任务复用
         * 如果这个任务需要重复使用，返回true，默认返回false
         *
         * @apiNote 当某队列具有无限制可复用IDLE任务时，需要使用{@link DispatchQueue#shutdown()}系列方法暂停分发队列
         *
         * @return 是否返回为复用任务
         */
        default boolean reuse() { return false; }
    }

    // CPU核心数量
    private static final int CPU_CORE = Runtime.getRuntime().availableProcessors();

    // 自定义线程池
    private static final Object REF_POOL_LOCK = new Object();
    private static int coroutinePoolRefCount = 0;
    private static ThreadPoolExecutor coroutinePool;

    private static final String DEFAULT_DISPATCHER_QUEUE = "DispatchQueue#default";
    private static final String DEFAULT_DISPATCHER_IO = "DispatchQueue#IO";
    private static final String DEFAULT_DISPATCHER_NAME = "default_DispatcherQueue#Name";
    private static final int ITEM_POOL_MAX = 20;

    private static final Object TOKEN_LOCK = new Object();
    private static final Set<String> TOKEN_SET = new HashSet<>();

    public static final DispatchQueue standard = new DispatchQueue(DEFAULT_DISPATCHER_QUEUE);
    public static final DispatchQueue io = new DispatchQueue(DEFAULT_DISPATCHER_IO, true);

    protected final String name;
    protected final boolean isCoroutine;

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

    protected final Object itemPoolLock = new Object();
    protected final Queue<TaskItem> itemPool = new LinkedList<>();

    protected long submitTaskId = 0;

    public DispatchQueue() {
        this(DEFAULT_DISPATCHER_NAME, false);
    }

    public DispatchQueue(boolean isCoroutine) {
        this(DEFAULT_DISPATCHER_NAME, isCoroutine);
    }

    public DispatchQueue(String name) {
        this(name, false);
    }

    public DispatchQueue(String name, boolean isCoroutine) {
        this.name = name;
        this.isCoroutine = isCoroutine;
        setStatus(Status.INIT);
        if(isCoroutine) {
            synchronized(REF_POOL_LOCK) {
                coroutinePoolRefCount++;
            }
        }
    }

    public static void once(String token, Task task) {
        once(token, 0, task);
    }

    public static void once(String token, long delay, Task task) {
        if(token == null || task == null) {
            return;
        }
        synchronized(TOKEN_LOCK) {
            if(TOKEN_SET.contains(token)) {
                return;
            }
            TOKEN_SET.add(token);
        }
        standard.async(delay, task);
    }

    private static ThreadPoolExecutor getOrCreatePool() {
        synchronized(REF_POOL_LOCK) {
            if(coroutinePool == null) {
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
            }
            return coroutinePool;
        }
    }

    private static void shutdownPoolIfNeed(boolean await) {
        synchronized(REF_POOL_LOCK) {
            if(coroutinePool == null || coroutinePoolRefCount < 0) {
                coroutinePoolRefCount = 0;
                return;
            }
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
            coroutinePool = null;
            coroutinePoolRefCount = 0;
        }
    }

    public void sync(Task task) {
        sync(0, null, task);
    }

    public void sync(Bundle bundle, Task task) {
        sync(0, bundle, task);
    }

    public void sync(long delay, Task task) {
        sync(delay, null, task);
    }

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

    public DispatchContext async(Task task) {
        return async(0, null, task);
    }

    public DispatchContext async(long delay, Task task) {
        return async(delay, null, task);
    }

    public DispatchContext async(Bundle bundle, Task task) {
        return async(0, bundle, task);
    }

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
        TaskItem item = obtain(delay, task, submitTaskId);
        synchronized(taskQueueLock) {
            if (status == Status.DESTROY) {
                recycle(item); // 回收任务项
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

    public void addIdle(IDLETask idle) {
        async(0, idle);
    }

    public void addIdle(long delay, IDLETask idle) {
        async(delay, idle);
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

    public void addStatusObserver(StatusObserver observer) {
        if(observer == null) {
            return;
        }
        synchronized(statusObserversLock) {
            statusObservers.add(observer);
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
                TaskItem item = obtain(0, task, submitTaskId);
                synchronized(taskQueueLock) {
                    taskQueue.offer(item);
                }
                return;
            }
            contextMap.remove(task);
            return;
        }
        ThreadPoolExecutor pool = getOrCreatePool();
        synchronized(REF_POOL_LOCK) {
            if(pool == null || pool.isShutdown()) { // 意外情况，降级处理
                task.run(context);
                return;
            }
            try {
                pool.submit(() -> {
                    task.run(context);
                });
            }
            catch(RejectedExecutionException e) { // 降级处理 直接执行
                task.run(context);
            }
        }
    }

    private void shutdown(boolean await, boolean clear) {
        if(status.rawValue < Status.DESTROY.rawValue) {
            synchronized(statusLock) {
                setStatus(Status.DESTROY);
            }
            synchronized(taskQueueLock) {
                if(clear) {
                    for (TaskItem each : taskQueue) {
                        recycle(each);
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
                synchronized(REF_POOL_LOCK) {
                    coroutinePoolRefCount--;
                }
                shutdownPoolIfNeed(await);
            }
        }
    }

    private TaskItem obtain(long delay, Task task, long id) {
        TaskItem item;
        synchronized(itemPoolLock) {
            item = itemPool.poll();
        }
        if(item == null) { // 池子内无元素说明全部正在使用
            item = new TaskItem(delay, task, id);
        }
        else {
            item.setDelay(delay);
            item.setTask(task);
            item.setId(id);
        }
        return item;
    }

    private void recycle(TaskItem task) {
        task.setTask(null);
        task.runMills = 0;
        task.delay = 0;
        task.id = Long.MAX_VALUE;
        synchronized(itemPoolLock) {
            if(itemPool.size() < ITEM_POOL_MAX) {
                itemPool.offer(task);
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
            lastIDLETimestamp = TimeUtil.now();
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
                DispatchQueue queue = this.ref.get();
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
                        if(queue == io) {
                            shutdownPoolIfNeed(false);
                        }
                    }
                    return;
                }
            }
        }

        private Task fetchTask(DispatcherQueueWeakRef ref) throws NeedDestroyDispatcherQueueException, InterruptedException {
            DispatchQueue q = ref.get();
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
                long itemRunMills = item.task instanceof IDLETask ? item.delay + q.lastIDLETimestamp : item.getRunMills();
                if(itemRunMills <= current) {
                    Task needExecute = item.getTask();
                    q.recycle(item);
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

    public enum Status {
        INIT(-1),
        BUSY(0),
        IDLE(1),
        DORMANT(2),
        DESTROY(3);

        public final int rawValue;

        Status(int rawValue) {
            this.rawValue = rawValue;
        }
    }

    public static class DispatcherQueueHasDestroyedException extends RuntimeException {
        private static final String MSG = " dispatcherQueue is destroyed. ";
        public DispatcherQueueHasDestroyedException(String queueName) {
            super(queueName + MSG);
        }
    }

    protected static final class TaskItem implements Comparable<TaskItem> {
        private static final int NORMAL_TASK = 0;
        private static final int IDLE_TASK = 1;
        private long runMills;
        private long delay;
        private Task task;
        private long id;

        public TaskItem(long delay, Task task, long id) {
            this.runMills = System.currentTimeMillis() + delay;
            this.delay = delay;
            this.task = task;
            this.id = id;
        }

        public long getRunMills() {
            return runMills;
        }

        public Task getTask() {
            return task;
        }

        public void setRunMills(long delay) {
            this.runMills = delay + System.currentTimeMillis();
        }

        public void setDelay(long delay) {
            this.runMills = System.currentTimeMillis() + delay;
            this.delay = delay;
        }

        public void setTask(Task task) {
            this.task = task;
        }

        public void setId(long id) {
            this.id = id;
        }

        @Override
        public int compareTo(TaskItem o) {
            int diff = taskLevel() - o.taskLevel();
            if(diff != 0) {
                return diff;
            }
            int result = taskLevel() == NORMAL_TASK ? Long.compare(runMills, o.runMills) : Long.compare(delay, o.delay);
            if(result == 0) {
                return Long.compare(id, o.id);
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaskItem taskItem = (TaskItem) o;
            return runMills == taskItem.runMills && (Objects.equals(task, taskItem.task));
        }

        @Override
        public int hashCode() {
            return Objects.hash(runMills, task);
        }

        private int taskLevel() {
            if(task instanceof IDLETask) {
                return IDLE_TASK;
            }
            return NORMAL_TASK;
        }
    }

    private static class DispatcherQueueWeakRef extends WeakReference<DispatchQueue> {
        public DispatcherQueueWeakRef(DispatchQueue referent) {
            super(referent);
        }
    }

    private static final class NeedDestroyDispatcherQueueException extends RuntimeException {
        private static final String MSG = " dispatcherQueue need to destroy. ";
        public NeedDestroyDispatcherQueueException(String queueName) {
            super(queueName + MSG);
        }
    }
}