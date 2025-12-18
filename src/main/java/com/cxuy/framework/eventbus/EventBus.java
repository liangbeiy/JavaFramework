/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus;

import com.cxuy.framework.annotation.Nullable;

public interface EventBus {
    void register(@Nullable Object subscriber);
    void unregister(@Nullable Object subscriber);
    void post(@Nullable Object event);
}
