/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.eventbus.annotate.Subscribe;
import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.util.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface EventBus {
    void register(@Nullable Object subscriber);
    void unregister(@Nullable Object subscriber);
    void post(@Nullable Object event);
}
