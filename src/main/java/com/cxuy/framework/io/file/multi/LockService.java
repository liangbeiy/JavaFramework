/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file.multi;

public interface LockService {
    @FunctionalInterface
    interface LockCallback {
        void callback(String path, boolean result);
    }

    void acquire(String filePath, LockCallback callback);

    void release(String filePath);
}
