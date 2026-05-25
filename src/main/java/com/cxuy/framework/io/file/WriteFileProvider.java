/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.provider.Provider;

public interface WriteFileProvider extends ReadFileProvider {
    String NAME = "WriteFileProvider";

    int MODE_WRITE = 0;
    int MODE_APPEND = 1;

    @FunctionalInterface
    interface WriteFileCallback {
        void callback(String path, int mode, boolean result);
    }

    void write(String path, String content, WriteFileCallback callback);

    void write(String path, byte[] bytes, WriteFileCallback callback);

    void append(String path, String content, WriteFileCallback callback);

    void append(String path, byte[] bytes, WriteFileCallback callback);
}
