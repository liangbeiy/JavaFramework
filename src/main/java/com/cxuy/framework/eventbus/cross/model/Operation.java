/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.model;

public enum Operation {
    REGISTER("register"), UNREGISTER("unregister"), POST("post");
    public final String rawValue;
    Operation(String rawValue) {
        this.rawValue = rawValue;
    }
}
