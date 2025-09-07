/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.protocol.builder;

import com.cxuy.framework.util.TextUtil;
import com.cxuy.http.client.Request;
import com.cxuy.http.protocol.Protocol;
import com.cxuy.http.protocol.annotation.Header;
import com.cxuy.http.protocol.annotation.method.HEAD;

import java.lang.annotation.Annotation;

public class HeaderBuilder implements RequestBuilder {
    public static final String NAME = "HeaderBuilder";
    @Override
    public void handleAnnotation(Protocol protocol, Annotation annotation, Object value, Request.Builder builder) {
        if(!(annotation instanceof Header header) || builder == null) {
            return;
        }
        String[] set = header.value();
        for(String entry : set) {
            String[] keyValue = splitByFirstEqual(entry);
            if(keyValue.length != 2) {
                continue;
            }
            builder.addHeader(keyValue[0], keyValue[1]);
        }
    }

    @Override
    public boolean responds(Annotation annotation) {
        return annotation instanceof Header;
    }

    private String[] splitByFirstEqual(String str) {
        if (TextUtil.isEmpty(str)) {
            return new String[]{ str };
        }
        int equalIndex = str.indexOf('=');
        if (equalIndex == -1) {
            return new String[]{ str };
        }
        String part1 = str.substring(0, equalIndex);
        String part2 = str.substring(equalIndex + 1);
        return new String[]{ part1, part2 };
    }
}
