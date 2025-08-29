/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.Logger;
import com.cxuy.http.client.Client;
import com.cxuy.http.client.Method;
import com.cxuy.http.client.Request;
import com.cxuy.http.client.Response;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.Header;
import com.cxuy.http.protocol.annotation.Param;
import com.cxuy.http.protocol.annotation.SubPath;
import com.cxuy.http.protocol.annotation.method.GET;
import com.cxuy.server.SimpleHttpServer;
import com.sun.net.httpserver.HttpServer;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer(new LifecycleObserver() {
            @Override
            public void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state) {
                if(state != LifecycleState.DID_START) {
                    return;
                }
                Protocol protocol = new Protocol.Builder()
                        .baseUrl("http://localhost:5867")
                        .build();
                I i = protocol.create(I.class);
                Request request = i.hello("kpb");
                Client.getInstance().async(request, new Client.ResponseCallback() {
                    @Override
                    public void response(Client client, Request request, Response response) {
                        String s = response.body.string();
                        Logger.d("MAIN", "response=" + s);
                    }
                });
            }
        });
        server.start();
        return;
    }

    public interface I {
        @GET(path = "/hello")
        @Header({"Content-Type=application/json;charset=utf-8"})
        Request hello(@Param("name")String name);
    }
}