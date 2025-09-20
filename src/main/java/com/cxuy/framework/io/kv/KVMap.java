/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.kv;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.context.Context;

import java.io.File;

public class KVMap implements MapStorage {
    public static final int MULTI_PROCESS = 0x0001;


    private static final String TAG = "KVMap";

    private static final String FOLDER_NAME = "kv_map";
    private static final String EXTENSION_NAME_KV_MAP = ".kv";

    private final String filePath;
    private final int mode;

    private KVMap(@Nullable Context context, String name, int mode) {
        if(context == null) {
            throw new RuntimeException("cannot create " + TAG + " instance, context is a null object");
        }
        filePath = context.getRootDir() +
                File.separator + FOLDER_NAME +
                File.separator + name + EXTENSION_NAME_KV_MAP;
        this.mode = mode;
    }

    @Override
    public void put(String key, String value) {

    }

    @Override
    public String get(String key, String value) {
        return "";
    }

    @Override
    public void remove(String key) {

    }

    @Override
    public void removeAll() {

    }

    @Override
    public boolean contains(String key) {
        return false;
    }
}
