/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy;

import com.cxuy.server.SimpleHttpServer;

public class Main {
    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer();
        server.start();
    }
}