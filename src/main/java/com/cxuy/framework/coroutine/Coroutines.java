/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Coroutines {
    private static final String TAG = "Coroutines";

    private static final String NAME_DISPATCHER_QUEUE_STANDARD = "com.cxuy.framework.coroutines#standard";
    private static final String NAME_DISPATCHER_QUEUE_IO = "com.cxuy.framework.coroutines#IO";

    public static final DispatchQueue standard = new DispatcherQueue(NAME_DISPATCHER_QUEUE_STANDARD);
    public static final DispatchQueue io = new DispatcherQueue(NAME_DISPATCHER_QUEUE_IO, true);

    private static class HOLDER {
        private static final Coroutines INSTANCE = new Coroutines();
    }

    public static Coroutines getDefault() {
        return HOLDER.INSTANCE;
    }

    private final Map<String, DispatchQueue> dispatchQueues = new ConcurrentHashMap<>();

    public DispatchQueue getCoroutine(String name) {
        return getCoroutine(name, DispatchQueue.class);
    }

    public <T extends DispatchQueue> T getCoroutine(String queueName, Class<T> clazz) {
        if(TextUtils.isEmpty(queueName)) {
            return null;
        }
        DispatchQueue queue = getDefaultQueue(queueName);
        if(queue == null) {
            queue = dispatchQueues.get(queueName);
            if(queue == null) {
                return null;
            }
        }
        try {
            return clazz.cast(queue);
        } catch (ClassCastException e) {
            Logger.e(TAG, "cast default queue is error, queue=" + queue.getClass().getSimpleName(), e);
        }
        return null;
    }

    public void addCoroutine(String name, DispatchQueue queue) {
        if(TextUtils.isEmpty(name)) {
            return;
        }
        DispatchQueue oldQueue = dispatchQueues.get(name);
        if(oldQueue != null) {
            oldQueue.shutdown();
        }
        dispatchQueues.put(name, queue);
    }

    public void removeCoroutine(String name) {
        if(TextUtils.isEmpty(name)) {
            return;
        }
        DispatchQueue oldQueue = dispatchQueues.get(name);
        if(oldQueue != null) {
            oldQueue.shutdown();
        }
        dispatchQueues.remove(name);
    }

    private DispatchQueue getDefaultQueue(String name) {
        if(TextUtils.isEmpty(name)) {
            return null;
        }
        switch(name) {
            case NAME_DISPATCHER_QUEUE_IO -> {
                return io;
            }
            case NAME_DISPATCHER_QUEUE_STANDARD -> {
                return standard;
            }
            default -> {
                return null;
            }
        }
    }

    private Coroutines() {  }
}
