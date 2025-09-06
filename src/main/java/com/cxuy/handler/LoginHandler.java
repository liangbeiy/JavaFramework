/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.handler;

import com.cxuy.server.Handler;
import com.cxuy.server.annotation.HandlerMethod;
import com.cxuy.server.annotation.HttpHandler;
import com.cxuy.server.annotation.Param;

@HttpHandler(path = "/login")
public class LoginHandler extends Handler {

    @HandlerMethod
    public String login(@Param("username") String userName, @Param("pwd") String password) {
        return "successful";
    }
}
