/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.server;

public enum HttpCode {
    SUCCESSFUL(200),
    ERROR(400),
    NOT_FOUND(404),
    SERVER_ERROR(500),
    OUT_OF_SERVER(503);

    public final int rawValue;
    private HttpCode(int code) {
        this.rawValue = code;
    }
}
