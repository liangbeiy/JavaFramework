/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.context;

public enum BuildConfig {
    DEBUG(0), RELEASE(1);
    public final int rawValue;

    BuildConfig(int rawValue) {
        this.rawValue = rawValue;
    }
}

