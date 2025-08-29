/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol.builder;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.Param;
import com.cxuy.http.protocol.annotation.SubPath;

import java.lang.annotation.Annotation;

public class SubPathBuilder implements RequestBuilder {
    public static final String NAME = "SubPathBuilder";

    @Override
    public void handleAnnotation(@NonNull Protocol protocol, @NonNull Annotation annotation, @Nullable Object value, @NonNull Request.Builder builder) {
        if(!(annotation instanceof SubPath path)) {
            return;
        }
        String subPath = path.value();
        build(builder, subPath, value);
    }

    @Override
    public boolean responds(Annotation annotation) {
        return annotation instanceof SubPath;
    }

    private void build(Request.Builder builder, String subPath, Object value) {
        if(builder == null) {
            return;
        }
        if(subPath == null || value == null) {
            return;
        }
        if(value instanceof String sub) {
            builder.setSubPath(subPath, sub);
        }
    }
}
