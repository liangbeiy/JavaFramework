/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.context.Context;
import com.cxuy.framework.context.FrameworkContext;
import com.cxuy.framework.eventbus.InterProcessEventBus;
import com.cxuy.framework.eventbus.annotate.Subscribe;
import com.cxuy.framework.eventbus.cross.InterProcessServer;
import com.cxuy.framework.io.file.FileManager;
import com.cxuy.framework.util.Logger;

public class Main {
    private static final String TAG = "MAIN";
    public static void main(String[] args) {
        FrameworkContext context = new FrameworkContext(FileManager.getInstance().getAbsolutePath());
        context.create();
        run(context);
        context.destroy();
    }

    public static void run(Context context) {
        // runAsServer();
        runAsClient();
    }

    public static void runAsServer() {
        InterProcessServer server = new InterProcessServer();
        server.start();
    }

    public static void runAsClient() {
        C c = new C();
        InterProcessEventBus.get().register(c);
    }

    public static class C {
        @Subscribe
        private void hello(String name) {
            Logger.d(TAG, "receive message: name" + name);
        }
    }
}