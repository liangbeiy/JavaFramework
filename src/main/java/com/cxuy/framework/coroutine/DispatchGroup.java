/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.coroutine.exception.GroupHasCommittedException;
import com.cxuy.framework.coroutine.exception.GroupHasDoneException;
import com.cxuy.framework.coroutine.exception.GroupStatusErrorException;
import com.cxuy.framework.coroutine.task.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class DispatchGroup {

    public DispatchGroup() {
        this(true);
    }

    public DispatchGroup(boolean autoLeave) {
        this.autoLeave = autoLeave;
    }

    private final boolean autoLeave;

    private final Object statusLock = new Object();
    private Status status = Status.BUILDING;

    private final Object builderLock = new Object();
    @Nullable
    private Transaction.Builder transactionBuilder;


    private final Semaphore semaphore = new Semaphore(0);

    public void async(DispatchQueue queue, Task task) {
        if(queue == null || task == null) {
            return;
        }
        checkStatus(Status.BUILDING);
        synchronized(builderLock) {
            if(transactionBuilder == null) {
                this.transactionBuilder = new Transaction.Builder();
            }
            transactionBuilder.append(queue, task);
        }
    }

    public void notify(DispatchQueue queue, Task task) {
        if(queue == null || task == null) {
            return;
        }
        synchronized(statusLock) {
            checkStatus(Status.BUILDING);
            status = Status.COMMITTED;
        }
        Transaction newTransaction;
        synchronized (builderLock) {
            if(transactionBuilder == null) {
                return;
            }
            newTransaction = transactionBuilder.notifyTask(queue, task)
                    .build();
            transactionBuilder = null;
        }
        // 开始执行
        Map<Task, DispatchQueue> tasks = newTransaction.tasks;
        for(Map.Entry<Task, DispatchQueue> entry : tasks.entrySet()) {
            entry.getValue().async(() -> {
                entry.getKey().run();
                if(autoLeave) {
                    semaphore.release();
                }
            });
        }
        new Thread(() -> {
            try {
                semaphore.acquire(tasks.size());
                newTransaction.notifyQueue.async(newTransaction.notifyTask);
                synchronized(statusLock) {
                    checkStatus(Status.COMMITTED);
                    status = Status.DONE;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void leave() {
        if(autoLeave) {
            return;
        }
        checkStatus(Status.COMMITTED);
        semaphore.release();
    }

    private void checkStatus(Status expected) {
        Status current;
        synchronized(statusLock) {
            current = expected;
        }
        if(current == status) {
            return;
        }
        if(current == Status.BUILDING) {
            throw new GroupStatusErrorException("the group's status is in error, and is expected to be" + current.name() + " instead of" + Status.BUILDING.name());
        }
        if(current == Status.COMMITTED) {
            throw new GroupHasCommittedException();
        }
        if(current == Status.DONE) {
            throw new GroupHasDoneException();
        }
    }

    private enum Status {
        BUILDING,
        COMMITTED,
        DONE;
    }

    private static final class Transaction {
        public final Map<Task, DispatchQueue> tasks;
        public final DispatchQueue notifyQueue;
        public final Task notifyTask;
        public Transaction(Map<Task, DispatchQueue> tasks, DispatchQueue notifyQueue, Task notifyTask) {
            this.tasks = tasks;
            this.notifyQueue  = notifyQueue;
            this.notifyTask = notifyTask;
        }

        public static final class Builder {
            private final Map<Task, DispatchQueue> tasks = new HashMap<>();
            private DispatchQueue notifyQueue;
            private Task notifyTask;

            public Builder append(DispatchQueue queue, Task task) {
                tasks.put(task, queue);
                return this;
            }

            public Builder remove(Task task) {
                tasks.remove(task);
                return this;
            }

            public Builder notifyTask(DispatchQueue queue, Task task) {
                notifyQueue = queue;
                this.notifyTask = task;
                return this;
            }

            public Transaction build() {
                return new Transaction(tasks, notifyQueue, notifyTask);
            }

        }
    }
}
