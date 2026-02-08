/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.message;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;
import com.cxuy.framework.eventbus.cross.protocol.data.PostData;

public class PostMessage extends Message<PostData> {
    public PostMessage(@NonNull PostData data) {
        super(Operation.POST, data);
    }
}
