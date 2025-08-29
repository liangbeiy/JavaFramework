/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.client;

import com.cxuy.framework.constant.Constant;
import com.cxuy.framework.util.TextUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request {
    public final Method method;
    public final String url;
    public final Map<String, Object> headers;
    public final Map<String, Object> params;
    public final String body;
    public final Map<String, File> files;
    public boolean followRedirects;

    private Request(Method method, String url, Map<String, Object> header, String body, Map<String, Object> params,
                    Map<String, File> files, boolean followRedirects) {
        this.method = method;
        this.url = url;
        headers = header;
        this.params = params;
        this.body = body; 
        this.files = files; 
        this.followRedirects = followRedirects; 
    }
    
    public static class Builder {
        // 匹配 {key} 格式的正则表达式（key 可包含字母、数字、下划线）
        private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

        private Method mMethod = Method.GET;
        private String mUrl;
        private Map<String, Object> mHeaders;
        private Map<String, Object> mParams;
        private String mBody;
        private Map<String, File> mFiles;
        private boolean mFollowRedirects = true;
        public Builder() {  }

        public Builder method(Method method) {
            this.mMethod = method;
            return this;
        }

        public Builder url(String url) {
            this.mUrl = url;
            return this;
        }

        public Builder setSubPath(String key, String subPath) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(mUrl);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String matchKey = matcher.group(1);
                if (TextUtil.equals(key, matchKey)) {
                    matcher.appendReplacement(result, subPath);
                } else {
                    matcher.appendReplacement(result, matcher.group(0));
                }
            }
            matcher.appendTail(result);
            mUrl = result.toString();
            return this;
        }

        public Builder header(Map<String, Object> headers) {
            this.mHeaders = headers;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.mParams = params;
            return this;
        }

        public Builder body(String body) {
            this.mBody = body;
            return this;
        }

        public Builder file(Map<String, File> files) {
            this.mFiles = files;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.mFollowRedirects = followRedirects;
            return this;
        }

        public Builder addHeader(String key, Object value) {
            if(key == null) {
                return this;
            }
            if(mHeaders == null) {
                mHeaders = new HashMap<>();
            }
            mHeaders.put(key, value);
            return this;
        }

        public Builder addParam(String key, Object value) {
            if(key == null) {
                return this;
            }
            if(mParams == null) {
                mParams = new HashMap<>();
            }
            mParams.put(key, value);
            return this;
        }

        public Builder addFile(String key, File value) {
            if(key == null) {
                return this;
            }
            if(mFiles == null) {
                mFiles = new HashMap<>();
            }
            mFiles.put(key, value);
            return this;
        }

        public Request build() {
            if(mUrl == null) {
                this.mUrl = Constant.EMPTY_STR;
            }
            if(mHeaders == null) {
                mHeaders = new HashMap<>();
            }
            if(mParams == null) {
                mParams = new HashMap<>();
            }
            if(mBody == null) {
                mBody = Constant.EMPTY_STR;
            }
            if(mFiles == null) {
                mFiles = new HashMap<>();
            }
            return new Request(mMethod, mUrl, mHeaders, mBody, mParams, mFiles, mFollowRedirects);
        }
    }
}
