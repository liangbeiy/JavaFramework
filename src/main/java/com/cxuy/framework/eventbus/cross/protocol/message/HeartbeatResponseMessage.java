/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.message;

import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;

public class HeartbeatResponseMessage extends Message<String> {

    private static class HOLDER {
        private static final HeartbeatResponseMessage INSTANCE = new HeartbeatResponseMessage();
    }
    public static HeartbeatResponseMessage get() {
        return HOLDER.INSTANCE;
    }

    private HeartbeatResponseMessage() {
        super(Operation.HEARTBEAT_RESPONSE, "heartbeat response");
    }
}
