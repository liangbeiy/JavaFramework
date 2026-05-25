/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.provider.Provider;

public interface ReadFileProvider extends FileProvider {
    String NAME = "ReadFileProvider";

    @FunctionalInterface
    interface ReadFileCallback<R> {
        void callback(String path, boolean result, R content);
    }

    void readString(String path, ReadFileCallback<String> callback);

    void readBytes(String path, ReadFileCallback<byte[]> callback);
}
