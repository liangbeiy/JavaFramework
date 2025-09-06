/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.kv;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;

import java.util.Map;
import java.util.Set;

public interface MapStorage {

    void put(@NonNull String key, String value);

    @Nullable
    String get(@NonNull String key, String value);

    void remove(@NonNull String key);

    void removeAll();

    boolean contains(@NonNull String key);
}
