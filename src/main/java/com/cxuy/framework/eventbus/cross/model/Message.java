/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.model;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.eventbus.cross.constant.Params;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;
import com.google.gson.annotations.SerializedName;

public class Message<T> {
    private static final String TAG = "Message";

    @SerializedName(Params.PARAM_NAME_OPERATION)
    public final Operation operation;
    @SerializedName(Params.PARAM_NAME_TYPE)
    public final String type;
    @SerializedName(Params.PARAM_NAME_DATA)
    public final T data;

    public static Message<String> registerMessage(Class<?> clazz) {
        return new Message<>(Operation.REGISTER, clazz,  "register client");
    }

    public static Message<String> unregisterMessage(Class<?> clazz) {
        return new Message<>(Operation.UNREGISTER, clazz, "unregister client");
    }

    public static Message<String> unregisterAllMessage() {
        return new Message<>(Operation.UNREGISTER, UnregisterAllModal.class, "unregister client");
    }

    public static <T> Message<T> postMessage(T data) {
        return new Message<>(Operation.POST, data.getClass(), data);
    }

    public static String encode(Message<?> message) {
        return JsonUtil.toJson(message) + "\n";
    }

    @Nullable
    public static <T> Message<T> decode(String json) {
        String typeName = JsonUtil.getValue(json, Params.PARAM_NAME_TYPE, String.class);
        if(TextUtil.isEmpty(typeName)) {
            Logger.e(TAG, "data_type is null or empty, check!!!");
            return null;
        }
        try {
            Class<?> clazz = Class.forName(typeName);
            return JsonUtil.fromJson(json, Message.class, clazz);
        } catch (ClassNotFoundException e) {
            Logger.e(TAG, "class not found, e=", e);
        }
        return null;
    }

    private Message(Operation operation, Class<?> clazz, T data) {
        this.operation = operation;
        this.type = clazz.getName();
        this.data = data;
    }
}
