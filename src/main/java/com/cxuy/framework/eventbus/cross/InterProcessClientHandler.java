/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.eventbus.cross.constant.Params;
import com.cxuy.framework.eventbus.cross.model.Message;
import com.cxuy.framework.eventbus.cross.model.Operation;
import com.cxuy.framework.lifecycle.DefaultLifecycleOwner;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class InterProcessClientHandler extends DefaultLifecycleOwner implements Runnable {
    private static final String TAG = "InterProcessClientHandler";
    @NonNull
    private final WeakReference<InterProcessServer> serverRef;
    private final Socket client;
    private BufferedReader reader;
    private OutputStream outputStream;

    public InterProcessClientHandler(InterProcessServer server,Socket socket) {
        this.serverRef = new WeakReference<>(server);
        this.client = socket;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            outputStream = socket.getOutputStream();
            new Thread(this).start();
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
            try {
                client.close();
                outputStream.close();
            } catch (IOException e) {
                // ignored
            }
            InterProcessServer server = serverRef.get();
            if(server != null) {
                server.close(this);
            }
        }
    }

    private void handleMessage(String jsonStr, Socket client) throws IOException {
        Operation operation = JsonUtil.getValue(jsonStr, Params.PARAM_NAME_OPERATION, Operation.class);
        String typeName = JsonUtil.optString(jsonStr, Params.PARAM_NAME_TYPE);
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
                server.registerClient(typeName, this);
                break;
            case Operation.POST:
                server.postMessage(this, typeName, jsonStr);
                break;
            case Operation.UNREGISTER:
                server.unregisterClient(typeName, this);
                break;
        }
    }

    public void receiveMessage(String messageJson) {
        if(outputStream == null || client.isClosed()) {
            return;
        }
        String standardJson = messageJson + "\n";
        try {
            outputStream.write(standardJson.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            Logger.e(TAG, "an exception occurred while output data");
        }
    }
}
