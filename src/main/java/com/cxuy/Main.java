/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.context.BuildConfig;
import com.cxuy.framework.context.Context;
import com.cxuy.framework.context.FrameworkContext;
import com.cxuy.framework.coroutine.Bundle;
import com.cxuy.framework.coroutine.DispatchContext;
import com.cxuy.framework.coroutine.DispatchGroup;
import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.io.file.FileManager;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.annotation.Param;
import com.cxuy.http.protocol.annotation.method.GET;

public class Main {
    private static final String TAG = "MAIN";
    public static void main(String[] args) {
        FrameworkContext context = new FrameworkContext(FileManager.getInstance().getAbsolutePath(), BuildConfig.DEBUG);
        context.create();
        run(context);
        context.destroy();
    }
  
    public static void run(Context context) {  }
}