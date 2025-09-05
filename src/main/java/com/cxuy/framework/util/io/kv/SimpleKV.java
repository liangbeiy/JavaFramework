/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util.io.kv;

import com.cxuy.framework.context.Context;

import java.io.File;

public class SimpleKV implements MapStorage {

    private static class HOLDER {

    }

    private final String storagePath;
    private final String name;
    public SimpleKV(Context context, String name) {
        storagePath = context.getRootDir();
        this.name = name;
        String path = storagePath + File.separator + name + ".kv";
    }
}
