/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol;

public enum Operation {
    REGISTER("register"),
    UNREGISTER("unregister"),
    POST("post"),
    HEARTBEAT_CHECK("heartbeat_check"),
    HEARTBEAT_RESPONSE("heartbeat_response");
    public final String rawValue;
    Operation(String rawValue) {
        this.rawValue = rawValue;
    }
}
