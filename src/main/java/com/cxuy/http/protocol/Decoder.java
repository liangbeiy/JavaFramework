/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol;

public interface Decoder {
    <T> T decode(String data, Class<T> clazz);
}
