/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross;

import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.eventbus.cross.model.Operation;
import com.cxuy.framework.eventbus.cross.model.UnregisterAllModal;
import com.cxuy.framework.lifecycle.DefaultLifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;

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
        acceptWorker.async(dispatchContext -> {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(9229);
                Logger.d(TAG, "socket port=" + 9229);
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
        unregisterClient(UnregisterAllModal.class.getName(), handler);
    }

    public void registerClient(String type, InterProcessClientHandler handler) {
        if(TextUtil.isEmpty(type) || handler == null) {
            return;
        }
        if(TextUtil.equals(UnregisterAllModal.class.getName(), type)) {
            Logger.w(TAG, "a message has been received, but its type is type of " + UnregisterAllModal.class.getSimpleName() + "UnregisterAll and operation is " + Operation.REGISTER.rawValue);
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
        if(TextUtil.isEmpty(type) || poster == null) {
            return;
        }
        Set<String> emptyEvent = new HashSet<>();
        if(TextUtil.equals(type, UnregisterAllModal.class.getName())) {
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

}
