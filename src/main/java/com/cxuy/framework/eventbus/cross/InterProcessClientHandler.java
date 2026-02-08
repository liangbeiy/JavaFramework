/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.coroutine.task.Task;
import com.cxuy.framework.eventbus.cross.protocol.Constants;
import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;
import com.cxuy.framework.eventbus.cross.protocol.data.PostData;
import com.cxuy.framework.eventbus.cross.protocol.message.HeartbeatCheckMessage;
import com.cxuy.framework.lifecycle.DefaultLifecycleOwner;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class InterProcessClientHandler extends DefaultLifecycleOwner implements Runnable {
    private static final String TAG = "InterProcessClientHandler";
    @NonNull
    private final WeakReference<InterProcessServer> serverRef;
    private final Socket client;
    private BufferedReader reader;
    private OutputStream outputStream;

    private final DispatchQueue heartbeatTaskQueue = new DispatcherQueue("com.cxuy.framework.InterProcessClientHandler#heartbeatQueue");

    @Nullable
    private Task heartbeatTimeoutTask = null;

    public InterProcessClientHandler(InterProcessServer server,Socket socket) {
        this.serverRef = new WeakReference<>(server);
        this.client = socket;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            outputStream = socket.getOutputStream();
            new Thread(this).start();
            heartbeatTaskQueue.async(Constants.HEARTBEAT_INTERVAL, () -> {
                checkClientForHeartbeat();
            });
        } catch (IOException e) {
            Logger.e(TAG, "getting reader occurred error", e);
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                Logger.d(TAG, "received a message");
                handleMessage(line, client);
            }
        } catch (IOException e) {
            Logger.e(TAG, "客户端断开：" + e.getMessage());
        } finally {
            Logger.d(TAG, "a client has disconnect inter-process bus, recycle the resources. ");
            close();
        }
    }

    private void handleMessage(String jsonStr, Socket client) throws IOException {
        Operation operation = JsonUtil.getValue(jsonStr, Message.PARAMS_KEY_OPERATION, Operation.class);
        if(operation == null) {
            Logger.w(TAG, "received a message, but op is null");
            return;
        }
        InterProcessServer server = serverRef.get();
        if(server == null) {
            Logger.w(TAG, "interProcess server has been shutdown");
            return;
        }
        Logger.d(TAG, "handle a message, operation=" + operation.rawValue);
        switch (operation) {
            case Operation.REGISTER:
                Set<String> registerData = JsonUtil.getValue(jsonStr, Message.PARAMS_KEY_DATA, new HashSet<>(), Set.class, String.class);
                for(String registerType : registerData) {
                    server.registerClient(registerType, this);
                }
                break;
            case Operation.POST:
                PostData postData = JsonUtil.getValue(jsonStr, Message.PARAMS_KEY_DATA, null, PostData.class);
                if(postData == null) {
                    Logger.w(TAG, "postData has been received but it is null");
                    break;
                }
                server.postMessage(this, postData.type, postData.value);
                break;
            case Operation.UNREGISTER:
                Set<String> unregisterData = JsonUtil.getValue(jsonStr, Message.PARAMS_KEY_DATA, new HashSet<>(), Set.class, String.class);
                for(String unregisterType : unregisterData) {
                    server.unregisterClient(unregisterType, this);
                }
                break;
            case Operation.HEARTBEAT_CHECK:
                server.responseHeartbeatCheck(this);
                break;
            case Operation.HEARTBEAT_RESPONSE:
                if(heartbeatTimeoutTask != null) {
                    heartbeatTaskQueue.remove(heartbeatTimeoutTask);
                    heartbeatTimeoutTask = null;
                }
                heartbeatTaskQueue.async(Constants.HEARTBEAT_INTERVAL, () -> {
                    checkClientForHeartbeat();
                });
                break;
        }
    }

    public void receiveMessage(String messageJson) {
        try {
            receive(messageJson);
        } catch (IOException e) {
            Logger.e(TAG, "an exception occurred while receiving message", e);
        }
    }

    public void close() {
        if(reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while client reader is closing...", e);
            }
        }
        if(outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while client output is closing...", e);
            }
        }
        if(!client.isClosed()) {
            try {
                client.close();
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while client socket is closing...", e);
            }
        }
        InterProcessServer server = serverRef.get();
        if(server != null) {
            server.close(this);
        }
    }

    private void checkClientForHeartbeat() {
        heartbeatTimeoutTask = () -> {
            Logger.i(TAG, "heartbeat check failure, timeout");
            close();
        };
        heartbeatTaskQueue.async(Constants.HEARTBEAT_TIMEOUT, heartbeatTimeoutTask);
        try {
            receive(JsonUtil.toJson(HeartbeatCheckMessage.get()));
        } catch (IOException e) {
            Logger.e(TAG, "an exception occurred while checking heartbeat", e);
            if(heartbeatTimeoutTask != null) {
                heartbeatTaskQueue.remove(heartbeatTimeoutTask);
                heartbeatTimeoutTask = null;
            }
            close();
        }
    }

    private void receive(String messageJson) throws IOException {
        if(outputStream == null || client.isClosed()) {
            return;
        }
        String standardJson = messageJson + "\n";
        outputStream.write(standardJson.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
