/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.task;

import com.cxuy.framework.coroutine.queue.DispatcherQueue;

/**
 * 停车任务
 * 在队列空闲时候，会取出这个任务执行
 */
public interface IDLETask extends Task {
    /**
     * 任务复用
     * 如果这个任务需要重复使用，返回true，默认返回false
     *
     * @apiNote 当某队列具有无限制可复用IDLE任务时，需要使用{@link DispatcherQueue#shutdown()}系列方法暂停分发队列
     *
     * @return 是否返回为复用任务
     */
    default boolean reuse() { return false; }
}
