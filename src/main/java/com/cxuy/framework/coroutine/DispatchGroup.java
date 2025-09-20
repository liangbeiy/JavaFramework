/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.coroutine.exception.GroupHasDoneException;

import java.util.*;
import java.util.concurrent.Semaphore;

public class DispatchGroup {

    public DispatchGroup() {
        this(true);
    }

    public DispatchGroup(boolean autoLeave) {
        this.autoLeave = autoLeave;
    }

    private boolean isDone = false;
    private final boolean autoLeave;
    private final Object builderLock = new Object();
    private final Semaphore semaphore = new Semaphore(0);

    @Nullable
    private Transaction.Builder transactionBuilder;

    public void async(DispatchQueue queue, DispatchQueue.Task task) {
        async(queue, null, task);
    }

    public void async(DispatchQueue queue, Bundle bundle, DispatchQueue.Task task) {
        if(queue == null || task == null) {
            return;
        }
        if(isDone) {
            throw new GroupHasDoneException();
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
        if(isDone) {
            throw new GroupHasDoneException();
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
        Map<DispatchQueue.Task, DispatchQueue> tasks = newTransaction.tasks;
        Map<DispatchQueue.Task, Bundle> bundles = newTransaction.bundles;
        for(Map.Entry<DispatchQueue.Task, DispatchQueue> entry : tasks.entrySet()) {
            entry.getValue().async(bundles.get(entry.getKey()), (context) -> {
                entry.getKey().run(context);
                if(autoLeave) {
                    semaphore.release();
                }
            });
        }
        DispatchQueue.io.async(bundle, (context) -> {
            try {
                semaphore.acquire(tasks.size());
                newTransaction.notifyQueue.async(bundle, newTransaction.notifyTask);
                isDone = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void leave() {
        if(isDone) {
            throw new GroupHasDoneException();
        }
        semaphore.release();
    }

    private static final class Transaction {
        public final Semaphore semaphore = new Semaphore(0);
        public final Map<DispatchQueue.Task, Bundle> bundles;
        public final Map<DispatchQueue.Task, DispatchQueue> tasks;
        public final DispatchQueue notifyQueue;
        public final DispatchQueue.Task notifyTask;
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
