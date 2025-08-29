/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol.builder;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.util.TextUtil;
import com.cxuy.http.client.Method;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.method.*;

import java.lang.annotation.Annotation;

public class MethodBuilder implements RequestBuilder {
    public static final String NAME = "MethodBuilder";
    @Override
    public void handleAnnotation(@NonNull Protocol protocol, Annotation annotation, Object value, Request.Builder builder) {
        if(builder == null) {
            return;
        }
        String path = getPath(annotation);
        if(!TextUtil.isEmpty(path)) {
            builder.url(protocol.getBaseUrl() + path);
        }
        setMethod(annotation, builder);
    }

    @Override
    public boolean responds(Annotation annotation) {
        return annotation instanceof DELETE || annotation instanceof GET ||
                annotation instanceof HEAD || annotation instanceof POST ||
                annotation instanceof OPTIONS || annotation instanceof  PATCH ||
                annotation instanceof PUT;
    }

    public String getPath(Annotation annotation) {
        if(annotation instanceof DELETE a) {
            return a.path();
        }
        if(annotation instanceof GET a) {
            return a.path();
        }
        if(annotation instanceof HEAD a) {
            return a.path();
        }
        if(annotation instanceof OPTIONS a) {
            return a.path();
        }
        if(annotation instanceof POST a) {
            return a.path();
        }
        if(annotation instanceof PATCH a) {
            return a.path();
        }
        if(annotation instanceof PUT a) {
            return a.path();
        }
        return null;
    }

    private void setMethod(@NonNull Annotation annotation, @NonNull Request.Builder builder) {
        if(annotation instanceof DELETE) {
            builder.method(Method.DELETE);
            return;
        }
        if(annotation instanceof GET) {
            builder.method(Method.GET);
            return;
        }
        if(annotation instanceof HEAD) {
            builder.method(Method.HEAD);
            return;
        }
        if(annotation instanceof POST) {
            builder.method(Method.POST);
            return;
        }
        if(annotation instanceof PATCH) {
            builder.method(Method.PATCH);
            return;
        }
        if(annotation instanceof PUT) {
            builder.method(Method.PUT);
            return;
        }
        builder.method(Method.OPTIONS);
    }
}
