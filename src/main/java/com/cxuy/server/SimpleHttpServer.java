/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.server;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.context.Context;
import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;
import com.cxuy.server.annotation.*;
import com.cxuy.server.model.JsonRpc;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleHttpServer implements Server, LifecycleOwner {

    private static final String TAG = "SimpleServer";

    private static final int DEFAULT_PORT = 5867;
    private static final int MAX_RETRY_TIMES = 10;

    private int port = DEFAULT_PORT;
    private HttpServer server;

    private final Object stateLock = new Object();
    private LifecycleState state = LifecycleState.ANY;

    private final Set<LifecycleObserver> observers = Collections.synchronizedSet(new HashSet<>());

    private final Context context;
    public SimpleHttpServer(Context context) {
        this(context, null);
    }

    public SimpleHttpServer(Context context, LifecycleObserver observer) {
        this.context = context.getFrameworkContext();
        if(observer != null) {
            addObserver(observer);
        }
        setState(LifecycleState.INITED);
    }

    public void start() {
        synchronized(stateLock) {
            if(state.rawValue > LifecycleState.DID_START.rawValue) {
                return;
            }
        }
        int nowTimes = 0;
        while(nowTimes < MAX_RETRY_TIMES && server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            } catch (IOException e) {
                nowTimes++;
                port += 1;
            }
        }
        if(server == null) {
            Logger.e(TAG, "SimpleHttpServer create server failed. ");
            return;
        }
        // 注册处理器
        registerHandler();

        server.start();
        setState(LifecycleState.DID_START);
        Logger.d(TAG, "SimpleHttpServer is running at " + port + " port. ");
    }

    public void stop() {
        if(state.rawValue < LifecycleState.DID_START.rawValue) {
            return;
        }
        server.stop(0);
        server = null;
        setState(LifecycleState.DID_STOP);
    }

    private void registerHandler() {
        try {
            Set<Class<?>> domain = HttpHandlerScanner.scanClass(context);
            for (Class<?> clazz : domain) {
                // 验证是否继承自Handler
                if (!Handler.class.isAssignableFrom(clazz)) {
                    String message = String.format("Warning: %s is ignored, because it uses @%s but does not extend %s", clazz.getName(), HttpHandler.class.getName(), Handler.class.getName());
                    Logger.w(TAG, message);
                    continue;
                }

                // 获取注解中的路径
                HttpHandler httpHandler = clazz.getAnnotation(HttpHandler.class);
                String path = httpHandler.path();
                if (path == null || path.isEmpty()) {
                    String message = String.format("Warning: %s is ignored, because there is a null path in its annotation. ", clazz.getName());
                    Logger.w(TAG, message);
                    continue;
                }

                // 创建处理器实例
                final Handler handlerInstance = createHandlerInstance(clazz);
                if (handlerInstance != null) {
                    List<String> handlePath = handlerInstance.getHandlePath();
                    for(String hPath : handlePath) {
                        // 注册处理器
                        server.createContext(hPath, handlerInstance);
                        String message = String.format("A handler has been registered by %s, path=%s -> handlerClass=%s", SimpleHttpServer.class.getName(), hPath, clazz.getName());
                        Logger.d(TAG, message);
                    }
                } else {
                    String message = String.format("Warning: %s is ignored, because this class cannot create instance.  ", clazz.getName());
                    Logger.w(TAG, message);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "An exception occurred while registering handler", e);
        }
    }

    private Handler createHandlerInstance(Class<?> clazz) {
        try {
            // 处理内部类
            if (clazz.isMemberClass()) {
                // 非静态内部类需要外部类实例
                if (!Modifier.isStatic(clazz.getModifiers())) {
                    Class<?> outerClass = clazz.getDeclaringClass();
                    Object outerInstance = outerClass.getDeclaredConstructor().newInstance();
                    return (Handler) clazz.getDeclaredConstructor(outerClass).newInstance(outerInstance);
                }
            }
            // 普通类或静态内部类
            return (Handler) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            Logger.e(TAG, "An exception occurred while creating instance", e);
            return null;
        }
    }

    @Override
    public LifecycleState getState() {
        synchronized(stateLock) {
            return this.state;
        }
    }

    @Override
    public void setState(LifecycleState state) {
        synchronized(stateLock) {
            this.state = state;
        }
        for(LifecycleObserver observer : observers) {
            observer.lifecycleOnChanged(this, state);
        }
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        if(observer == null || observers.contains(observer)) {
            return;
        }
        observers.add(observer);
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        if(observer == null || !observers.contains(observer)) {
            return;
        }
        observers.remove(observer);
    }
}
