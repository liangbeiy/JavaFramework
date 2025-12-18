/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.task;

import com.cxuy.framework.coroutine.DispatchContext;

@FunctionalInterface
public interface Task {
    void run(DispatchContext context);
}
