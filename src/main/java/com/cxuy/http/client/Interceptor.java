/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.client;

public interface Interceptor {
    default void beforeSubmitRequest(Client client, Request request) {  }
    default void submitRequest(Client client, Request request, boolean successful) {  }
    default void beforeHandleRequest(Client client, Request request) {  }
    default void handleRequest(Client client, Request request, Response response) {  }
}
