/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.handler;

import com.cxuy.server.SimpleHttpServer;
import com.cxuy.server.annotation.HandlerMethod;
import com.cxuy.server.annotation.HttpHandler;

@HttpHandler(path = "/hello")
public class HelloHandler extends SimpleHttpServer.Handler {

    @HandlerMethod(subPath = "/hello")
    public String hello() {
        return "hello world";
    }

    @HandlerMethod
    public String hello(String name) {
        return "hello, " + name;
    }
}
