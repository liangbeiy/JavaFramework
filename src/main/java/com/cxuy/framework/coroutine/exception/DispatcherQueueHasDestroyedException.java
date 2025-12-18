/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.exception;

public class DispatcherQueueHasDestroyedException extends RuntimeException {
    private static final String MSG = " dispatcherQueue is destroyed. ";
    public DispatcherQueueHasDestroyedException(String queueName) {
        super(queueName + MSG);
    }
}
