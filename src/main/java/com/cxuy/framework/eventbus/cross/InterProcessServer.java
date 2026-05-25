/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross;

import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.eventbus.cross.protocol.Constants;
import com.cxuy.framework.eventbus.cross.protocol.Operation;
import com.cxuy.framework.eventbus.cross.protocol.message.HeartbeatResponseMessage;
import com.cxuy.framework.eventbus.cross.protocol.message.UnregisterMessage;
import com.cxuy.framework.lifecycle.DefaultLifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class InterProcessServer extends DefaultLifecycleOwner {
    private static final String TAG = "InterProcessServer";
    // 订阅关系：事件类型 -> 客户端连接集合
    private final Map<String, Set<InterProcessClientHandler>> subscribers = Collections.synchronizedMap(new HashMap<>());

    private final DispatcherQueue acceptWorker = new DispatcherQueue("com.cxuy.framework.eventbus.InterProcessServer#accpter");
    public InterProcessServer() {
        setState(LifecycleState.INITED);
    }

    public void start() {
        setState(LifecycleState.WILL_START);
        acceptWorker.async(() -> {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(Constants.SOCKET_SERVER_START_PORT);
                Logger.d(TAG, "socket port=" + Constants.SOCKET_SERVER_START_PORT);
                setState(LifecycleState.DID_START);
                while(getState().rawValue < LifecycleState.WILL_DESTROY.rawValue) {
                    Socket client = serverSocket.accept();
                    Logger.d(TAG, "socket has been accepted and handle");
                    new InterProcessClientHandler(this, client);
                }
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while create server socket, e=", e);
                setState(LifecycleState.DID_STOP);
            } finally {
                if(serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Logger.e(TAG, "closing server socket has failure, e=", e);
                    }
                }
            }
        });
    }

    public void close(InterProcessClientHandler handler) {
        unregisterClient(UnregisterMessage.DATA_UNREGISTER_ALL, handler);
    }

    public void registerClient(String type, InterProcessClientHandler handler) {
        if(TextUtils.isEmpty(type) || handler == null) {
            return;
        }
        if(TextUtils.equals(UnregisterMessage.DATA_UNREGISTER_ALL, type)) {
            Logger.w(TAG, "a message has been received, but its type is type of " + UnregisterMessage.DATA_UNREGISTER_ALL + "UnregisterAll and operation is " + Operation.REGISTER.rawValue);
            return;
        }
        Set<InterProcessClientHandler> handlers = subscribers.get(type);
        if(handlers == null) {
            handlers = new HashSet<>();
        }
        handlers.add(handler);
        subscribers.put(type, handlers);
    }

    public void postMessage(InterProcessClientHandler poster, String type, String messageJson) {
        Set<InterProcessClientHandler> handlers = subscribers.get(type);
        if(handlers == null) {
            return;
        }
        for(InterProcessClientHandler handler : handlers) {
            handler.receiveMessage(messageJson);
        }
    }

    public void unregisterClient(String type, InterProcessClientHandler poster) {
        if(TextUtils.isEmpty(type) || poster == null) {
            return;
        }
        Set<String> emptyEvent = new HashSet<>();
        if(TextUtils.equals(UnregisterMessage.DATA_UNREGISTER_ALL, type)) {
            for(Map.Entry<String, Set<InterProcessClientHandler>> entry : subscribers.entrySet()) {
                Set<InterProcessClientHandler> handlers = entry.getValue();
                handlers.remove(poster);
                if(handlers.isEmpty()) {
                    emptyEvent.add(entry.getKey());
                }
            }
        } else {
            Set<InterProcessClientHandler> handlers = subscribers.get(type);
            if(handlers == null) {
                return;
            }
            handlers.remove(poster);
            if(handlers.isEmpty()) {
                emptyEvent.add(type);
            }
        }
        for(String typeName : emptyEvent) {
            subscribers.remove(typeName);
        }
    }

    public void responseHeartbeatCheck(InterProcessClientHandler handler) {
        handler.receiveMessage(JsonUtil.toJson(HeartbeatResponseMessage.get()));
    }

}
