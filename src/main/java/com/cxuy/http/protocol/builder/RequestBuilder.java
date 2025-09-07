/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol.builder;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.Protocol;

import java.lang.annotation.Annotation;

public interface RequestBuilder {
    void handleAnnotation(@NonNull Protocol protocol, @NonNull Annotation annotation, @Nullable Object value, @NonNull Request.Builder builder);

    boolean responds(Annotation annotation);
}
