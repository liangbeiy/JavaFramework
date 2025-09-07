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

    public static void run(Context context) {
//        I protocol = new Protocol.Builder().baseUrl("http://localhost:5867").build().create(I.class);
//        SimpleHttpServer server = new SimpleHttpServer(context, new LifecycleObserver() {
//            @Override
//            public void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state) {
//                if(state == LifecycleState.DID_START) {
//                    Client.getInstance().async(protocol.login("kpb", "123"), new Client.ResponseCallback() {
//                        @Override
//                        public void response(Client client, Request request, Response response) {
//                            Logger.d(TAG, response.body.string());
//                        }
//                    });
//                }
//            }
//        });
//        server.start();

        DispatchGroup group = new DispatchGroup();
        Bundle bundle = new Bundle();
        bundle.putExtra("INTEGER", 3);
        group.async(DispatchQueue.standard, bundle, (context1) -> {
            Logger.d(TAG, "s INTEGER=" + context1.getBundle().getInt("INTEGER", 0));
        });
        group.async(DispatchQueue.io, bundle, (context1) -> {
            Logger.d(TAG, "io INTEGER=" + context1.getBundle().getInt("INTEGER", 0));
        });
        group.async(DispatchQueue.io, bundle, (context1) -> {
            Logger.d(TAG, "io INTEGER=" + context1.getBundle().getInt("INTEGER", 0));
        });
        group.notify(DispatchQueue.standard, bundle, (context1) -> {
            Logger.d(TAG, "notify INTEGER=" + context1.getBundle().getInt("INTEGER", 0));
        });
        Logger.d(TAG, "finish");
    }

    public interface I {
        @GET(path = "/hello")
        Request hello(@Param("name") String name);
        @GET(path = "/login")
        Request login(@Param("username") String name, @Param("pwd")String password);
    }
}