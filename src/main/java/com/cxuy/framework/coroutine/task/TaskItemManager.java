/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.task;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;

import java.util.LinkedList;
import java.util.Queue;

public final class TaskItemManager {
    private static final int ITEM_POOL_MAX = 20;
    private static class HOLDER {
        private static final TaskItemManager INSTANCE = new TaskItemManager();
    }

    public static TaskItemManager get() {
        return HOLDER.INSTANCE;
    }

    private final Object itemPoolLock = new Object();
    private final Queue<TaskItem> itemPool = new LinkedList<>();

    @Nullable
    public TaskItem acquire(long delay, long id, @NonNull Task task) {
        if(task == null) {
            return null;
        }
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

    public void recycle(TaskItem task) {
        if(task == null) {
            return;
        }
        task.setTask(null);
        task.setRunMills(0);
        task.setDelay(0);
        task.setId(Long.MAX_VALUE);
        synchronized(itemPoolLock) {
            if(itemPool.size() < ITEM_POOL_MAX) {
                itemPool.offer(task);
            }
        }
    }

    private TaskItemManager() {  }
}
