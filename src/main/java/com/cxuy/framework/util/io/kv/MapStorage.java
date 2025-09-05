/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util.io.kv;

import com.cxuy.framework.annotation.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MapStorage {
    void put(String key, short value);
    void put(String key, int value);
    void put(String key, long value);

    void put(String key, float value);
    void put(String key, double value);

    void put(String key, byte value);

    void put(String key, char value);

    void put(String key, boolean value);

    <T> void put(String key, List<T> set);

    <T> void put(String key, Set<T> set);

    <T> void put(String key, Map<String, T> set);

    @Nullable
    <T> T get(String key);

    <T> T get(String key, T defaultValue);
}
