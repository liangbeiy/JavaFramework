/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.protocol.message;

import com.cxuy.framework.eventbus.cross.protocol.Message;
import com.cxuy.framework.eventbus.cross.protocol.Operation;

import java.util.HashSet;
import java.util.Set;

public class RegisterMessage extends Message<Set<String>> {

    public RegisterMessage(Set<Class<?>> data) {
        super(Operation.REGISTER, new HashSet<>());
        for(Class<?> clazz : data) {
            this.data.add(clazz.getName());
        }
    }
}
