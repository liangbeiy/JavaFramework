/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.message;

import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;

public class HeartbeatCheckMessage extends Message<String> {
    private static class HOLDER {
        private static final HeartbeatCheckMessage INSTANCE = new HeartbeatCheckMessage();
    }
    public static HeartbeatCheckMessage get() {
        return HeartbeatCheckMessage.HOLDER.INSTANCE;
    }
    public HeartbeatCheckMessage() {
        super(Operation.HEARTBEAT_CHECK, "heartbeat check");
    }
}
