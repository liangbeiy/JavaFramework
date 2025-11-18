/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.lifecycle;

import com.cxuy.framework.util.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class DefaultLifecycleOwner implements LifecycleOwner {
    private static final String TAG = "DefaultLifecycleOwner";

    private final Set<LifecycleObserver> observers = Collections.synchronizedSet(new HashSet<>());
    private final Object stateLock = new Object();
    private LifecycleState state = LifecycleState.ANY;

    @Override
    public LifecycleState getState() {
        LifecycleState current;
        synchronized(stateLock) {
            current = state;
        }
        return current;
    }

    @Override
    public void setState(LifecycleState state) {
        synchronized(stateLock) {
            Logger.d(TAG, "state has been changed, current=" + this.state.name() + "next state=" + state.name());
            this.state = state;
            for(LifecycleObserver observer : observers) {
                observer.lifecycleOnChanged(this, state);
            }
        }
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        if(observer == null) {
            Logger.d(TAG, "fail to add observer with observer is null object");
            return;
        }
        observers.add(observer);
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        if(observer == null) {
            Logger.d(TAG, "fail to remove observer with observer is null object");
            return;
        }
        observers.remove(observer);
    }
}
