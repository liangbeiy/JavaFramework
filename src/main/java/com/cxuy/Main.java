/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.framework.context.BuildConfig;
import com.cxuy.framework.context.Context;
import com.cxuy.framework.context.FrameworkContext;
import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.io.file.FileManager;
import com.cxuy.framework.io.kv.SimpleKV;
import com.cxuy.http.client.Client;
import com.cxuy.http.client.Request;
import com.cxuy.http.client.Response;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.Param;
import com.cxuy.http.protocol.annotation.method.GET;
import com.cxuy.server.SimpleHttpServer;

import java.net.http.HttpClient;
import java.util.Map;

public class Main {
    private static final String TAG = "MAIN";
    public static void main(String[] args) {
        FrameworkContext context = new FrameworkContext(FileManager.getInstance().getAbsolutePath(), BuildConfig.DEBUG);
        context.create();
        run(context);
        context.destroy();
    }

    public static void run(Context context) {
        I protocol = new Protocol.Builder().baseUrl("http://localhost:5867").build().create(I.class);
        SimpleHttpServer server = new SimpleHttpServer(context, new LifecycleObserver() {
            @Override
            public void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state) {
                if(state == LifecycleState.DID_START) {
                    Client.getInstance().async(protocol.hello("kpb"), new Client.ResponseCallback() {
                        @Override
                        public void response(Client client, Request request, Response response) {
                            Logger.d(TAG, response.body.string());
                        }
                    });
                }
            }
        });
        server.start();
    }

    public interface I {
        @GET(path = "/hello")
        Request hello(@Param("name") String name);
    }
}