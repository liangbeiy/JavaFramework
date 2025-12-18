/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.coroutine.queue.OnceDispatcherQueue;
import com.cxuy.framework.coroutine.task.IDLETask;
import com.cxuy.framework.coroutine.task.Task;

public interface DispatchQueue {
    String DEFAULT_DISPATCHER_QUEUE = "DispatchQueue#standard";
    String DEFAULT_DISPATCHER_IO = "DispatchQueue#IO";
    OnceDispatcherQueue once = OnceDispatcherQueue.get();
    DispatchQueue standard = new DispatcherQueue(DEFAULT_DISPATCHER_QUEUE);
    DispatchQueue io = new DispatcherQueue(DEFAULT_DISPATCHER_IO, true);

    interface StatusObserver {
        void onChanged(DispatcherQueue queue, Status status);
    }

    enum Status {
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

    void sync(@NonNull Task task);
    void sync(@Nullable Bundle bundle, @NonNull Task task);
    void sync(long delay, @NonNull Task task);
    void sync(long delay, @Nullable Bundle bundle, @NonNull Task task);

    DispatchContext async(@NonNull Task task);
    DispatchContext async(@Nullable Bundle bundle, @NonNull Task task);
    DispatchContext async(long delay, @NonNull Task task);
    DispatchContext async(long delay, @Nullable Bundle bundle, @NonNull Task task);

    DispatchContext addIdle(@NonNull IDLETask task);
    DispatchContext addIdle(long delay, IDLETask idle);
    DispatchContext addIdle(@Nullable Bundle bundle, @NonNull IDLETask task);
    DispatchContext addIdle(long delay, Bundle bundle, IDLETask idle);

    void shutdown();
    void awaitShutdown();
    void shutdown(boolean await, boolean isClear);

    void addObserver(@Nullable StatusObserver observer);
    void removeObserver(@Nullable StatusObserver observer);
}
