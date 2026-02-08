/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol;

public class Constants {
    /**心跳间隔60s*/
    public static final int HEARTBEAT_INTERVAL = 60 * 1000;
    /**心跳响应超时间隔120s*/
    public static final int HEARTBEAT_TIMEOUT = 2 * HEARTBEAT_INTERVAL;

    public static final int SOCKET_SERVER_START_PORT = 5729;

}
