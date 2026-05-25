/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.queue;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.coroutine.task.Task;

import java.util.HashSet;
import java.util.Set;

@Deprecated(since = "1.31.5.0")
public final class OnceDispatcherQueue extends DispatcherQueue {
    private static final String ONCE_DISPATCHER_QUEUE_NAME = "DispatchQueue#once";
    private static final Object TOKEN_LOCK = new Object();
    private static final Set<String> TOKEN_SET = new HashSet<>();

    private static final class HOLDER {
        private static final OnceDispatcherQueue INSTANCE = new OnceDispatcherQueue();
    }

    @Deprecated(since = "1.31.5.0")
    public static OnceDispatcherQueue get() {
        return HOLDER.INSTANCE;
    }

    private OnceDispatcherQueue() {
        super(ONCE_DISPATCHER_QUEUE_NAME);
    }

    @Deprecated(since = "1.31.5.0")
    public void once(@NonNull String token, @NonNull Task task) {
        once(0, token, task);
    }

    @Deprecated(since = "1.31.5.0")
    public static void once(long delay, @NonNull String token, @NonNull Task task) {
        if(token == null || task == null) {
            return;
        }
        synchronized(TOKEN_LOCK) {
            if(TOKEN_SET.contains(token)) {
                return;
            }
            TOKEN_SET.add(token);
        }
        HOLDER.INSTANCE.async(delay, task);
    }
}
