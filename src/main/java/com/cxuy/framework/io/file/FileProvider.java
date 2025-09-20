/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.provider.Provider;

public interface FileProvider extends Provider {
    String NAME = "FileProvider";

    @FunctionalInterface
    interface ReadFileCallback<R> {
        void callback(String path, R content);
    }

    @FunctionalInterface
    interface WriteFileCallback {
        void callback(String path, int mode, boolean result);
    }

    boolean isExist(String path);

    boolean create(String path);

    void readString(String path, ReadFileCallback<String> callback);

    void readBytes(String path, ReadFileCallback<byte[]> callback);

    void writeString(String path, String content, WriteFileCallback callback);

    void writeBytes(String path, byte[] bytes, WriteFileCallback callback);

    void appendString(String path, String content, WriteFileCallback callback);

    void appendBytes(String path, byte[] bytes, WriteFileCallback callback);

    void delete(String path);

    void mkdir(String path);

    void copy(String sourcePath, String destinationPath);

    void move(String sourcePath, String destinationPath);
}
