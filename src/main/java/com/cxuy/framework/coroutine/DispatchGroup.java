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

    public void async(DispatchQueue queue, DispatchQueue.Task task) {
        if(queue == null || task == null) {
            return;
        }
        synchronized(builderLock) {
            if(transactionBuilder == null) {
                this.transactionBuilder = new Transaction.Builder();
            }
            transactionBuilder.append(queue, null, task);
        }
    }

    public void async(DispatchQueue queue, Bundle bundle, DispatchQueue.Task task) {
        if(queue == null || task == null) {
            return;
        }
        synchronized(builderLock) {
            if(transactionBuilder == null) {
                this.transactionBuilder = new Transaction.Builder();
            }
            transactionBuilder.append(queue, bundle, task);
        }
    }

    public void notify(DispatchQueue queue, DispatchQueue.Task task) {
        notify(queue, null, task);
    }

    public void notify(DispatchQueue queue, Bundle bundle, DispatchQueue.Task task) {
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
        DispatchQueue.standard.async((context) -> {
            Map<DispatchQueue.Task, DispatchQueue> tasks = newTransaction.tasks;
            Map<DispatchQueue.Task, Bundle> bundles = newTransaction.bundles;
            final Semaphore semaphore = new Semaphore(0);
            for(Map.Entry<DispatchQueue.Task, DispatchQueue> entry : tasks.entrySet()) {
                entry.getValue().async(bundles.get(entry.getKey()), (executeContext) -> {
                    entry.getKey().run(executeContext);
                    semaphore.release();
                });
            }
            DispatchQueue.io.async(bundle, (notifyContext) -> {
                try {
                    semaphore.acquire(tasks.size());
                    newTransaction.notifyQueue.async(bundle, newTransaction.notifyTask);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
    }

    private static final class Transaction {
        public final Map<DispatchQueue.Task, Bundle> bundles;
        public final Map<DispatchQueue.Task, DispatchQueue> tasks;
        public DispatchQueue notifyQueue;
        public DispatchQueue.Task notifyTask;
        public Transaction(Map<DispatchQueue.Task, Bundle> bundles, Map<DispatchQueue.Task, DispatchQueue> tasks, DispatchQueue notifyQueue, DispatchQueue.Task notifyTask) {
            this.bundles = bundles;
            this.tasks = tasks;
            this.notifyQueue  = notifyQueue;
            this.notifyTask = notifyTask;
        }

        public static final class Builder {
            public final Map<DispatchQueue.Task, Bundle> bundles = new HashMap<>();
            private final Map<DispatchQueue.Task, DispatchQueue> tasks = new HashMap<>();
            private DispatchQueue notifyQueue;
            private DispatchQueue.Task notifyTask;

            public Builder append(DispatchQueue queue, Bundle bundle, DispatchQueue.Task task) {
                tasks.put(task, queue);
                if(bundle != null) {
                    bundles.put(task, bundle);
                }
                return this;
            }

            public Builder remove(DispatchQueue.Task task) {
                tasks.remove(task);

                return this;
            }

            public Builder notifyTask(DispatchQueue queue, DispatchQueue.Task task) {
                notifyQueue = queue;
                this.notifyTask = task;
                return this;
            }

            public Transaction build() {
                return new Transaction(bundles, tasks, notifyQueue, notifyTask);
            }

        }
    }
}
