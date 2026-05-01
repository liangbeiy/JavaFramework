/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.provider.Provider;

public interface FileProvider extends Provider {
    String NAME = "FileProvider";

    boolean isExist(String path);

    void create(String path);

    void delete(String path);

    void mkdir(String path);

    void copy(String sourcePath, String destinationPath);

    void move(String sourcePath, String destinationPath);
}
