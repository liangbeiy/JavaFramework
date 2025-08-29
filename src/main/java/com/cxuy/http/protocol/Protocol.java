/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.util.Logger;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.builder.RequestBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.List;

public final class Protocol implements InvocationHandler {
    private static final String TAG = "Protocol";

    private final String baseUrl;
    private final Encoder encoder;

    public Protocol(String baseUrl, Encoder encoder) {
        this.baseUrl = baseUrl;
        this.encoder = encoder;
    }

    public <T> T create(Class<T> clazz) {
        boolean vail = isVail(clazz);
        return clazz.cast(Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{ clazz }, this));
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Encoder getEncoder() {
        return encoder;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的 equals hashCode等方法
        if (method.getDeclaringClass() == Object.class) {
            // 调用代理对象自身的实现
            return method.invoke(this, args);
        }
        Request.Builder resultBuilder = new Request.Builder();
        Annotation[] annotations = method.getDeclaredAnnotations();
        for(Annotation annotation : annotations) {
            List<RequestBuilder> builders = BuilderProvider.getInstance().getRespondBuilder(annotation);
            //检查是否唯一
            RequestBuilder builder = checkUniqueBuilder(builders);
            if(builder != null) {
                builder.handleAnnotation(this, annotation, null, resultBuilder);
            }
        }
        Parameter[] parameters = method.getParameters();
        for(int i = 0; i < parameters.length; i++) {
            Annotation[] paramAnnotations = parameters[i].getAnnotations();
            for(Annotation annotation : paramAnnotations) {
                List<RequestBuilder> builders = BuilderProvider.getInstance().getRespondBuilder(annotation);
                //检查是否唯一
                RequestBuilder builder = checkUniqueBuilder(builders);
                if(builder != null) {
                    builder.handleAnnotation(this, annotation, args[i], resultBuilder);
                }
            }
        }
        return resultBuilder.build();
    }

    private <T> boolean isVail(Class<T> clazz) {
        return true;
    }

    @Nullable
    private RequestBuilder checkUniqueBuilder(List<RequestBuilder> builders) {
        if(builders.isEmpty()) {
            Logger.w(TAG, "There is any builder response building request");
            return null;
        }
        if(builders.size() != 1) {
            Logger.w(TAG, "There are many builders response building request");
            return null;
        }
        return builders.getFirst();
    }

    public static final class Builder {
        private String mBaseUrl;
        private Encoder mEncoder;

        public Builder() {  }

        public Builder baseUrl(String url) {
            this.mBaseUrl = url;
            return this;
        }

        public Builder decoder(Encoder decoder) {
            this.mEncoder = decoder;
            return this;
        }

        public Protocol build() {
            return new Protocol(mBaseUrl, mEncoder);
        }
    }
}
