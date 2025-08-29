/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.lifecycle;

public enum LifecycleState {
    ANY(-1), 
    INITED(0), 

    WILL_CREATE(1), 
    DID_CREATE(2), 

    WILL_START(3), 
    DID_START(4), 

    WILL_RESUME(5), 
    DID_RESUME(6), 

    WILL_PAUSE(7), 
    DID_PAUSE(8), 

    WILL_STOP(9), 
    DID_STOP(10), 
    
    WILL_DESTROY(11), 
    DID_DESTROY(12); 

    public final int rawValue;

    private LifecycleState(int rawValue) {
        this.rawValue = rawValue; 
    }
}
