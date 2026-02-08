/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.data;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.util.JsonUtil;

public final class PostData {
    public final String type;
    public final String value;

    private PostData(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public static <T> PostData create(@NonNull T element) {
        String jsonValue = JsonUtil.toJson(element);
        if(jsonValue == null) {
            return new PostData(element.getClass().getName(), "");
        }
        return new PostData(element.getClass().getName(), jsonValue);
    }
}
