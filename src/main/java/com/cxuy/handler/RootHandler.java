/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.handler;

import com.cxuy.server.SimpleHttpServer;
import com.cxuy.server.annotation.HandlerMethod;
import com.cxuy.server.annotation.HttpHandler;

@HttpHandler(path = "/")
public class RootHandler extends SimpleHttpServer.Handler {

    @HandlerMethod
    public String root() {
        return "Anything resource not be contain at root. ";
    }

    @HandlerMethod(subPath = "/root")
    public String rot() {
        return "Anything resource not be contain at root. ";
    }
}
