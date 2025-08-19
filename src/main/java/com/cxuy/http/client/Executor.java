/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.client;

import com.cxuy.framework.util.Logger;
import com.cxuy.http.exception.InvalidMethodException;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;

public class Executor {
    private static final String TAG = "Executor";

    private static final String KEY_CONTENT_TYPE = "Content-Type";
    private static final String BOUNDARY_PREFIX = "---------------------------";

    private final Request request;
    private final int connectTimeout;
    private final int readTimeout;

    public Executor(Request request, int connectTimeout, int readTimeout) {
        this.request = request;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public Response execute() throws IOException {
        String url = buildUrl();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            config(connection);
            handleRequestBody(connection);
            return request(connection);
        } catch (URISyntaxException e) {
            Logger.e(TAG, "An exception occurred while request HTTP", e);
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private String buildUrl() {
        StringBuilder url = new StringBuilder(request.url + "?");
        for(Map.Entry<String, Object> entry : request.params.entrySet()) {
            url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        if(!url.isEmpty()) {
            url.deleteCharAt(url.length() - 1);
        }
        return url.toString();
    }

    private void config(HttpURLConnection connection) {
        // 设置请求方法
        try {
            connection.setRequestMethod(request.method.name());
        } catch (ProtocolException e) {
            throw new InvalidMethodException(request.method.name());
        }

        // 设置超时
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);

        // 设置是否跟随重定向
        connection.setInstanceFollowRedirects(request.followRedirects);

        // 添加请求头
        for (Map.Entry<String, Object> entry : request.headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue().toString());
        }

        // 如果有请求体或文件，需要设置DoOutput为true
        if (request.body != null || !request.files.isEmpty() ||
                (request.method != Method.GET && !request.params.isEmpty())) {
            connection.setDoOutput(true);
        }
    }

    /**
     * 处理请求体
     */
    private void handleRequestBody(HttpURLConnection connection) throws IOException {
        Map<String, File> files = request.files;
        Map<String, Object> params = request.params;
        String body = request.body;

        // 有文件上传，使用multipart/form-data
        if (!files.isEmpty()) {
            handleMultipartData(connection, params, files);
        }
        // 没有文件但有参数，使用form-urlencoded（适用于POST等方法）
        else if (request.method != Method.GET && !params.isEmpty()) {
            handleFormData(connection, params);
        }
        else if (body != null) {
            handleRawBody(connection, body);
        }
    }

    /**
     * 处理文件上传（multipart/form-data）
     */
    private void handleMultipartData(HttpURLConnection connection, Map<String, Object> params, Map<String, File> files) throws IOException {
        String boundary = BOUNDARY_PREFIX + new Random().nextLong();
        connection.setRequestProperty(KEY_CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

            // 写入普通参数
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                writer.write("--" + boundary + "\r\n");
                writer.write("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n");
                writer.write(entry.getValue() + "\r\n");
            }

            // 写入文件
            for (Map.Entry<String, File> entry : files.entrySet()) {
                String fieldName = entry.getKey();
                File file = entry.getValue();

                writer.write("--" + boundary + "\r\n");
                writer.write("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                        + file.getName() + "\"\r\n");
                writer.write("Content-Type: " + URLConnection.guessContentTypeFromName(file.getName()) + "\r\n");
                writer.write("Content-Transfer-Encoding: binary\r\n\r\n");
                writer.flush();

                // 写入文件内容
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.flush();
                }

                writer.write("\r\n");
            }

            // 结束边界
            writer.write("--" + boundary + "--\r\n");
            writer.flush();
        }
    }

    /**
     * 处理表单数据
     */
    private void handleFormData(HttpURLConnection connection, Map<String, Object> params) throws IOException {
        if (!connection.getRequestProperties().containsKey(KEY_CONTENT_TYPE)) {
            connection.setRequestProperty(KEY_CONTENT_TYPE, "application/x-www-form-urlencoded");
        }

        try (OutputStream os = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!postData.isEmpty()) {
                    postData.append('&');
                }
                postData.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
            }

            writer.write(postData.toString());
            writer.flush();
        }
    }

    /**
     * 处理原始请求体（如JSON）
     */
    private void handleRawBody(HttpURLConnection connection, String body) throws IOException {
        try (OutputStream os = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write(body);
            writer.flush();
        }
    }

    private Response request(HttpURLConnection connection) throws IOException {
        int statusCode = connection.getResponseCode();
        String statusMessage = connection.getResponseMessage();

        // 获取响应头
        Map<String, String> headers = new java.util.HashMap<>();
        connection.getHeaderFields().forEach((key, values) -> {
            if (key != null && values != null && !values.isEmpty()) {
                headers.put(key, values.getFirst());
            }
        });

        // 获取响应体
        byte[] body;
        try (InputStream is = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
             ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            if (is != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    stream.write(buffer, 0, bytesRead);
                }
            }
            body = stream.toByteArray();
        }

        return new Response(statusCode, statusMessage, headers, body);
    }
}
