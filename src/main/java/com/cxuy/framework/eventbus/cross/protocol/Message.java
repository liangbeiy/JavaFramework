/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.constant.Constant;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.TextUtil;
import com.cxuy.framework.util.TimeUtil;

public class Message<T> {
    public static final String PARAMS_KEY_OPERATION = "operation";
    public static final String PARAMS_KEY_DATA = "data";

    public final Operation operation;
    public final T data;
    public final long timestamp;

    public Message(@NonNull Operation operation, @NonNull T data) {
        this.operation = operation;
        this.data = data;
        this.timestamp = TimeUtil.now();
    }

    @NonNull
    public String encode() {
        String jsonData = JsonUtil.toJson(this);
        if(TextUtil.isEmpty(jsonData)) {
            return Constant.EMPTY_STR;
        }
        return jsonData;
    }
}
