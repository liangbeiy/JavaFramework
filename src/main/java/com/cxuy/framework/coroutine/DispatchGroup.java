/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.annotation.Nullable;

import java.util.*;
import java.util.concurrent.Semaphore;

public class DispatchGroup {

    private final Object builderLock = new Object();
    @Nullable
    private Transaction.Builder transactionBuilder;

    public void async(DispatcherQueue queue, DispatcherQueue.Task task) {
        if(queue == null || task == null) {
            return;
        }
        synchronized(builderLock) {
            if(transactionBuilder == null) {
                this.transactionBuilder = new Transaction.Builder();
            }
            transactionBuilder.append(queue, task);
        }
    }

    public void notify(DispatcherQueue queue, DispatcherQueue.Task task) {
        if(queue == null || task == null) {
            return;
        }
        Transaction newTransaction;
        synchronized(builderLock) {
            if(transactionBuilder == null) {
                return;
            }
            newTransaction = transactionBuilder
                    .notifyTask(queue, task)
                    .build();
            transactionBuilder = null;
        }
        DispatcherQueue.standard.async(() -> {
            Map<DispatcherQueue.Task, DispatcherQueue> tasks = newTransaction.tasks;
            final Semaphore semaphore = new Semaphore(0);
            for(Map.Entry<DispatcherQueue.Task, DispatcherQueue> entry : tasks.entrySet()) {
                entry.getValue().async(() -> {
                    entry.getKey().run();
                    semaphore.release();
                });
            }
            DispatcherQueue.io.async(() -> {
                try {
                    semaphore.acquire(tasks.size());
                    newTransaction.notifyQueue.async(newTransaction.notifyTask);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
    }

    private static final class Transaction {
        public final Map<DispatcherQueue.Task, DispatcherQueue> tasks;
        public DispatcherQueue notifyQueue;
        public DispatcherQueue.Task notifyTask;
        public Transaction(Map<DispatcherQueue.Task, DispatcherQueue> tasks, DispatcherQueue notifyQueue, DispatcherQueue.Task notifyTask) {
            this.tasks = tasks;
            this.notifyQueue  = notifyQueue;
            this.notifyTask = notifyTask;
        }

        public static final class Builder {
            private final Map<DispatcherQueue.Task, DispatcherQueue> tasks = new HashMap<>();
            private DispatcherQueue notifyQueue;
            private DispatcherQueue.Task notifyTask;

            public Builder append(DispatcherQueue queue, DispatcherQueue.Task task) {
                tasks.put(task, queue);
                return this;
            }

            public Builder remove(DispatcherQueue.Task task) {
                tasks.remove(task);
                return this;
            }

            public Builder notifyTask(DispatcherQueue queue, DispatcherQueue.Task task) {
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
