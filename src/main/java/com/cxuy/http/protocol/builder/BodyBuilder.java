/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol.builder;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.Body;

import java.lang.annotation.Annotation;

public class BodyBuilder implements RequestBuilder {
    public static final String NAME = "BodyBuilder";

    @Override
    public void handleAnnotation(@NonNull Protocol protocol, Annotation annotation, Object value, Request.Builder builder) {
        if(!(annotation instanceof Body) || builder == null || protocol.getEncoder() == null) {
            return;
        }
        if(value instanceof String json) {
            builder.body(json);
            return;
        }
        String jsonStr = protocol.getEncoder().encode(value);
        builder.body(jsonStr);
    }

    @Override
    public boolean responds(Annotation annotation) {
        return annotation instanceof Body;
    }


}
