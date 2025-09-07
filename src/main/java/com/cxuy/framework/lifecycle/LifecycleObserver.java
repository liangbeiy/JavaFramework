/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.lifecycle;

public interface LifecycleObserver {
    void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state); 
}
