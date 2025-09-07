/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.annotation.Nullable;

public class DispatchContext {
    private final DispatchQueue dispatchQueue;
    private final long taskId;

    private final Object cancelLock = new Object();
    private boolean isCancelled;

    private final Object bundleLock = new Object();
    private Bundle bundle;

    public DispatchContext(long taskId, DispatchQueue queue) {
        this.taskId = taskId;
        this.dispatchQueue = queue;
    }

    public DispatchQueue getDispatcherQueue() {
        return dispatchQueue;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setBundle(Bundle bundle) {
        synchronized(bundleLock) {
            this.bundle = bundle;
        }
    }

    @Nullable
    public Bundle getBundle() {
        return bundle;
    }

    public void cancel() {
        synchronized (cancelLock) {
            isCancelled = true;
        }
    }

    public boolean isCancel() {
        boolean hasCancelled;
        synchronized (cancelLock) {
            hasCancelled = isCancelled;
        }
        return hasCancelled;
    }
}
