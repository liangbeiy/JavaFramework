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

import java.lang.annotation.Annotation;
import java.util.NavigableMap;

public class ParamBuilder implements RequestBuilder {
    public static final String NAME = "ParamBuilder";

    @Override
    public void handleAnnotation(@NonNull Protocol protocol, @NonNull Annotation annotation, @Nullable Object value, @NonNull Request.Builder builder) {
        if(!(annotation instanceof Param param)) {
            return;
        }
        String paramName = param.value();
        build(builder, paramName, value);
    }

    @Override
    public boolean responds(Annotation annotation) {
        return annotation instanceof Param;
    }

    private void build(Request.Builder builder, String key, Object value) {
        if(builder == null) {
            return;
        }
        if(key != null && value != null) {
            builder.addParam(key, value);
        }
    }
}
