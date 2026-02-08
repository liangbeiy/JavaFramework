/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.task;

import java.util.Objects;

public final class TaskItem implements Comparable<TaskItem> {
    private static final int NORMAL_TASK = 0;
    private static final int IDLE_TASK = 1;
    private long runMills;
    private long delay;
    private Task task;
    private long id;

    public TaskItem(long delay, Task task, long id) {
        this.runMills = System.currentTimeMillis() + delay;
        this.delay = delay;
        this.task = task;
        this.id = id;
    }

    public long getRunMills() {
        return runMills;
    }

    public Task getTask() {
        return task;
    }

    public void setRunMills(long delay) {
        this.runMills = delay + System.currentTimeMillis();
    }

    public void setDelay(long delay) {
        this.runMills = System.currentTimeMillis() + delay;
        this.delay = delay;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDelay() {
        return delay;
    }

    @Override
    public int compareTo(TaskItem o) {
        int diff = taskLevel() - o.taskLevel();
        if(diff != 0) {
            return diff;
        }
        int result = taskLevel() == NORMAL_TASK ? Long.compare(runMills, o.runMills) : Long.compare(delay, o.delay);
        if(result == 0) {
            return Long.compare(id, o.id);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskItem taskItem = (TaskItem) o;
        return runMills == taskItem.runMills && (Objects.equals(task, taskItem.task));
    }

    @Override
    public int hashCode() {
        return Objects.hash(runMills, task);
    }

    private int taskLevel() {
        if(task instanceof IDLETask) {
            return IDLE_TASK;
        }
        return NORMAL_TASK;
    }
}
