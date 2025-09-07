/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.constant;

public enum Global {
    DEFAULT("1.0.0", 10000);

    public final int versionCode;
    public final String versionName;

    Global(String versionName, int versionCode) {
        this.versionName = versionName;
        this.versionCode = versionCode;
    }
}
