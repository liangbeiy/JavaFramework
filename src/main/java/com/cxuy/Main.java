/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.context.Context;
import com.cxuy.framework.context.FrameworkContext;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.io.file.FileManager;
import com.cxuy.framework.io.kv.SimpleKV;

import java.util.Map;

public class Main {
    private static final String TAG = "MAIN";
    public static void main(String[] args) {
        FrameworkContext context = new FrameworkContext(FileManager.getInstance().getAbsolutePath());
        context.create();
        run(context);
        context.destroy();
    }

    public static void run(Context context) {
        SimpleKV kv = new SimpleKV(context);
        kv.remove("hello");
        kv.putString("helloStr", "hello");
        kv.putString("helloStrDel", "hellod");
        kv.putFloat("helloStrFloat", 3.5f);
        kv.remove("helloStrDel");
        kv.putMap("hellomap", Map.of("1", "1", "2", "2"));
        kv.apply();
        Logger.d(TAG, "helloFloat=" + kv.getFloat("helloStrFloat", 0f));
    }
}