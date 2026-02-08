/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.task;

import com.cxuy.framework.annotation.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public final class TaskItemManager {
    private static final int ITEM_POOL_MAX = 20;
    private static class HOLDER {
        private static final TaskItemManager INSTANCE = new TaskItemManager();
    }

    public static TaskItemManager get() {
        return HOLDER.INSTANCE;
    }

    private final Object busyItemPoolLock = new Object();
    private final Map<Long, TaskItem> busyItemMap = new HashMap<>();

    private final Object freeItemPoolLock = new Object();
    private long taskId = 0;
    private final Queue<TaskItem> freeItemPool = new LinkedList<>();

    @Nullable
    public TaskItem create(long delay, @Nullable Task task) {
        if(task == null) {
            return null;
        }
        TaskItem item;
        long assignedId;
        synchronized(freeItemPoolLock) {
            item = freeItemPool.poll();
            assignedId = taskId;
            taskId++;
        }
        if(item == null) { // 池子内无元素说明全部正在使用
            item = new TaskItem(delay, task, assignedId);
        }
        else {
            item.setDelay(delay);
            item.setTask(task);
            item.setId(assignedId);
        }
        synchronized(busyItemPoolLock) {
            busyItemMap.put(assignedId, item);
        }
        return item;
    }

    @Nullable
    public TaskItem open(long taskId) {
        TaskItem item;
        synchronized(busyItemPoolLock) {
            item = busyItemMap.get(taskId);
        }
        return item;
    }

    public void recycle(TaskItem task) {
        if(task == null) {
            return;
        }
        task.setTask(null);
        task.setRunMills(0);
        task.setDelay(0);
        task.setId(Long.MAX_VALUE);
        synchronized(freeItemPoolLock) {
            if(freeItemPool.size() < ITEM_POOL_MAX) {
                freeItemPool.offer(task);
            }
        }
    }

    private TaskItemManager() {  }
}
