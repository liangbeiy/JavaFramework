/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.client;

import com.cxuy.framework.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    public final int statusCode;
    public final String statusMessage;
    public final Map<String, String> header;
    public final Body body;

    public Response(int statusCode, String statusMessage, Map<String, String> header, byte[] body) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.header = new HashMap<>(header);
        this.body = body != null ? new Body(body) : Body.EMPTY_BODY;
    }

    public static class Body {
        private static final Body EMPTY_BODY = new Body(new byte[0]);

        private final byte[] body;
        @Nullable
        private String asString;

        public Body(byte[] body) {
            this.body = body;
        }

        public String string() {
            if(asString == null) {
                asString = new String(body, StandardCharsets.UTF_8);
            }
            return asString;
        }
    }
}
