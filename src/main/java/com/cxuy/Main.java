/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.util.Logger;
import com.cxuy.http.client.Client;
import com.cxuy.http.client.Method;
import com.cxuy.http.client.Request;
import com.cxuy.server.SimpleHttpServer;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer();
        server.start();
        Map<String, Object> map = new HashMap<>();
        map.put("name", "kpbhttp");
        Client.getInstance().async(
                new Request.Builder().method(Method.GET).url("http://localhost:5867/hello")
                        .params(map)
                        .build(),
                (client, request, response) -> Logger.d("Main", response.body.string()));
    }
}