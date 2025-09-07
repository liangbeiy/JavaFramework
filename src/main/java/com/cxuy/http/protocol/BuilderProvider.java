/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.http.protocol.builder.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuilderProvider {

    private static class HOLDER {
        public static BuilderProvider INSTANCE = new BuilderProvider();
    }
    public static BuilderProvider getInstance() {
        return HOLDER.INSTANCE;
    }

    private final Object lock = new Object();
    private final Map<String, RequestBuilder> builders = new HashMap<>();

    private BuilderProvider() {
        addBuilder(BodyBuilder.NAME, new BodyBuilder());
        addBuilder(HeaderBuilder.NAME, new HeaderBuilder());
        addBuilder(MethodBuilder.NAME, new MethodBuilder());
        addBuilder(ParamBuilder.NAME, new ParamBuilder());
        addBuilder(SubPathBuilder.NAME, new SubPathBuilder());
    }

    public void addBuilder(String name, RequestBuilder builder) {
        if(name == null || builder == null) {
            return;
        }
        synchronized (lock) {
            builders.put(name, builder);
        }
    }

    public <T> T getBuilder(String name, Class<T> clazz) {
        if(name == null) {
            return null;
        }
        synchronized (lock) {
            return clazz.cast(builders.get(name));
        }
    }

    @NonNull
    public List<RequestBuilder> getRespondBuilder(@Nullable Annotation annotation) {
        List<RequestBuilder> list = new ArrayList<>();
        synchronized(lock) {
            for(Map.Entry<String, RequestBuilder> entry : builders.entrySet()) {
                RequestBuilder builder = entry.getValue();
                if(builder.responds(annotation)) {
                    list.add(builder);
                }
            }
        }
        return list;
    }

    public void removeBuilder(String name) {
        if(name == null) {
            return;
        }
        synchronized (lock) {
            builders.remove(name);
        }
    }
}
