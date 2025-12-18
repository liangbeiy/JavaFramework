/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus.cross.decenter;

public enum Role {
    PEER("peer", -1),
    MASTER("master", 0),
    CANDIDATE("candidate", 1),
    CLIENT("client", 2);

    public final String rawName;
    public final int rawValue;

    Role(String name, int value) {
        this.rawName = name;
        this.rawValue = value;
    }
}
