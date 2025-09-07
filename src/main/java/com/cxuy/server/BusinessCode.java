/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.server;

public enum BusinessCode {
    SUCCESSFUL(0),
    FAILURE(1),
    PARAMS_ERROR(2);

    private static final int BASE_CODE = 200;

    public final int rawValue;
    private BusinessCode(int code) {
        this.rawValue = BASE_CODE + code;
    }
}
