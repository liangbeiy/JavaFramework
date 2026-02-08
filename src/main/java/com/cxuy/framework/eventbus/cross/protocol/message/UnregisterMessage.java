/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.message;

import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;

import java.util.Set;

public class UnregisterMessage extends Message<Set<String>> {
    public static final String DATA_UNREGISTER_ALL = "data_unregister_all";

    private static class HOLDER {
        private static final UnregisterMessage UNREGISTER_ALL = new UnregisterMessage(Set.of(DATA_UNREGISTER_ALL));
    }

    public static UnregisterMessage unregisterAll() {
        return HOLDER.UNREGISTER_ALL;
    }

    public UnregisterMessage(Set<String> data) {
        super(Operation.UNREGISTER, data);
    }
}
