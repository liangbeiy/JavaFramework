/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus;

import com.cxuy.framework.coroutine.DispatchContext;
import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.eventbus.annotate.Subscribe;
import com.cxuy.framework.eventbus.cross.InterProcessServer;
import com.cxuy.framework.eventbus.cross.constant.Constants;
import com.cxuy.framework.eventbus.cross.constant.Params;
import com.cxuy.framework.eventbus.cross.model.Message;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class InterProcessEventBus implements EventBus {
    private static final String TAG = "InterProcessEventBus";
    private static final String TCP_HOST = "localhost";
    private static final int TCP_PORT = 9229; // 与服务端端口一致

    private static class HOLDER {
        private static final InterProcessEventBus INSTANCE = new InterProcessEventBus();
    }

    private final Object pendingQueueLock = new Object();
    private final Queue<PendingMessage> pendingMessages = new LinkedList<>();
    private final DispatcherQueue interWorker = new DispatcherQueue(InterProcessServer.class.getName() + "#inter");
    private final DispatcherQueue worker = new DispatcherQueue(InterProcessServer.class.getName() + "#worker");

    private final Map<Class<?>, Set<SubscriberWrapper>> subscribers = new HashMap<>();
    private final Map<Object, SubscriberWrapper> registers = new HashMap<>();

    private Socket socket;
    private OutputStream outputStream;
    private BufferedReader reader;

    private DispatchContext cancelContext = null;
    private final DispatcherQueue.Task heartbeatTask = (heartbeatContext) -> {
        if(cancelContext != null) {
            cancelContext.cancel();
        }
        Message<String> message = Message.heartbeatMessage();
        sendMessage(null, message);
        cancelContext = worker.async(Constants.HEARTBEAT_TIMEOUT, (context) -> {
            Logger.e(TAG, "Heartbeat timeout, reconnecting...");
            closeSocket();
            connect();
        });
        worker.async(Constants.HEARTBEAT_INTERVAL, get().heartbeatTask);
    };

    public static InterProcessEventBus get() {
        return HOLDER.INSTANCE;
    }

    private InterProcessEventBus() {
        connect();
        worker.async(heartbeatTask);
    }


    @Override
    public void register(Object subscriber) {
        if(subscriber == null) {
            return;
        }
        worker.async((context) -> {
            if(registers.containsKey(subscriber)) {
                return;
            }
            // 生成包装实例-被扫描出来的
            SubscriberWrapper wrapper = generateWrapper(subscriber);
            registers.put(subscriber, wrapper);
            // 发送消息进行注册
            for(Class<?> event : wrapper.getEvents()) {
                Message<?> message = Message.registerMessage(event);
                register(subscriber, message);
            }
        });
    }

    @Override
    public void unregister(Object subscriber) {
        Message<?> message = Message.unregisterAllMessage();
        worker.async((context) -> {
            unregister(subscriber, message);
        });
    }

    @Override
    public void post(Object event) {
        Message<?> message = Message.postMessage(event);
        worker.async((context) -> {
            post(message);
        });
    }

    private void register(Object subscriber, Message<?> message) {
        if(subscriber == null || message == null) {
            return;
        }
        boolean registerIsSuccessful = sendMessage(subscriber, message);
        if(!registerIsSuccessful) {
            return;
        }
        try {
            Class<?> event = Class.forName(message.type);
            SubscriberWrapper wrapper = registers.get(subscriber);
            if(wrapper == null) {
                return;
            }
            wrapper.activeMethod(event);
            Set<SubscriberWrapper> subSet = subscribers.get(event);
            if(subSet == null) {
                subSet = new HashSet<>();
                subscribers.put(event, subSet);
                subSet.add(wrapper);
            }
        } catch (ClassNotFoundException e) {
            Logger.e(TAG, "cannot find class of name=" + message.type);
        }
    }

    private void post(Message<?> message) {
        sendMessage(null, message);
    }

    private void unregister(Object subscriber, Message<?> message) {
        sendMessage(subscriber, message);
    }

    private boolean sendMessage(Object poster, Message<?> message) {
        if(!isConnect()) {
            pendingMessages.add(new PendingMessage(poster, message));
            return false;
        }
        String json = Message.encode(message);
        try {
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            pendingMessages.add(new PendingMessage(poster, message));
        }
        return false;
    }

    private void handleEvent(String messageJson) {
        Message<?> message = Message.decode(messageJson);
        if(message == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(message.type);
            Object data = JsonUtil.getValue(messageJson, Params.PARAM_NAME_DATA, clazz);
            Set<SubscriberWrapper> wrappers = subscribers.get(clazz);
            for(SubscriberWrapper wrapper : wrappers) {
                wrapper.handleEvent(clazz, data);
            }
        } catch (ClassNotFoundException e) {
            Logger.e(TAG, "error", e);
        }
    }

    private void connect() {
        try {
            socket = new Socket(TCP_HOST, TCP_PORT);
            outputStream = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Logger.d(TAG, "inter process connected...");
            startReceive();
            handlePendingMessage();
        } catch (IOException e) {
            Logger.e(TAG, "fail to connect inter process server", e);
            closeSocket();
        }
    }

    public void handlePendingMessage() {
        // 处理全部挂起事件
        DispatcherQueue.io.async(context -> {
            synchronized(pendingQueueLock) {
                Queue<PendingMessage> copyList = new LinkedList<>(pendingMessages);
                pendingMessages.clear();
                for(PendingMessage message : copyList) {
                    switch (message.message.operation) {
                        case REGISTER -> register(message.poster, message.message);
                        case POST -> post(message.message);
                        case UNREGISTER -> unregister(message.poster, message.message);
                    }
                }
            }
        });
    }

    private boolean isConnect() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void startReceive() {
        interWorker.async((context) -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    Logger.d(TAG, "a message has been received...");
                    handleEvent(line);
                }
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred", e);
                closeSocket();
            }
        });
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            /* 忽略 */
        }
        socket = null;
        outputStream = null;
        reader = null;
    }

    private SubscriberWrapper generateWrapper(Object subscriber) {
        // 获取订阅者的类名与全部注册响应的方法
        Class<?> clazz = subscriber.getClass();
        Set<Method> annotatedMethods = new HashSet<>();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                annotatedMethods.add(method);
            }
        }
        SubscriberWrapper wrapper = new SubscriberWrapper(subscriber);
        // 对全部方法按照事件类型生成map
        for(Method method : annotatedMethods) {
            Class<?> paramClass = method.getParameterTypes()[0];
            Set<Method> wrapperMethod = wrapper.allMethods.get(paramClass);
            if(wrapperMethod == null) {
                wrapperMethod = new HashSet<>();
                wrapper.allMethods.put(paramClass, wrapperMethod);
            }
            wrapperMethod.add(method);
        }
        return wrapper;
    }

    private record PendingMessage(Object poster, Message<?> message) {  }

    private static class SubscriberWrapper {
        private final Object subscriber;
        private final Map<Class<?>, Set<Method>> allMethods;
        private final Map<Class<?>, Set<Method>> activeMethods;

        public SubscriberWrapper(Object s) {
            subscriber = s;
            allMethods = new HashMap<>();
            activeMethods = new HashMap<>();
        }

        public void handleEvent(Class<?> paramClass, Object event) {
            Set<Method> handleMethods = this.activeMethods.get(paramClass);
            if(handleMethods == null) {
                return;
            }
            for(Method method : handleMethods) {
                try {
                    method.invoke(subscriber, event);
                } catch (IllegalAccessException e) {
                    Logger.e(TAG, "EventBus: arguments is error", e);
                } catch (InvocationTargetException e) {
                    Logger.e(TAG, "EventBus: arguments is error, InvocationTargetException", e);
                } catch(Exception e) {
                    Logger.e(TAG, "EventBus: exception occurred. ", e);
                }
            }
        }

        public Set<Class<?>> getEvents() {
            return allMethods.keySet();
        }

        public void activeMethod(Class<?> event) {
            if(event == null) {
                return;
            }
            Set<Method> methods = allMethods.remove(event);
            if(methods != null) {
                activeMethods.put(event, methods);
            }
        }
    }
}
