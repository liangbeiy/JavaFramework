/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.context;

import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FrameworkContext extends Context implements LifecycleOwner {

    private final Object stateLock = new Object();
    private LifecycleState state;

    private final Set<LifecycleObserver> observers = Collections.synchronizedSet(new HashSet<>());

    public FrameworkContext(String root) {
        super(root);
        setState(LifecycleState.INITED);
    }

    public void create() {
        handleDestroy();
    }

    public void destroy() {
        handleDestroy();
    }

    private void handleCreate() {
        setState(LifecycleState.WILL_CREATE);
        onCreate();
        setState(LifecycleState.DID_CREATE);
    }

    private void handleDestroy() {
        setState(LifecycleState.WILL_DESTROY);
        onDestroy();
        setState(LifecycleState.DID_DESTROY);
    }
    public void onCreate() {  }
    public void onDestroy() {
        setState(LifecycleState.WILL_DESTROY);
    }

    @Override
    public LifecycleState getState() {
        LifecycleState s;
        synchronized (stateLock) {
            s = state;
        }
        return s;
    }

    @Override
    public void setState(LifecycleState state) {
        synchronized (stateLock) {
            this.state = state;
        }
        for(LifecycleObserver observer : observers) {
            observer.lifecycleOnChanged(this, state);
        }
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        if(observer == null) {
            return;
        }
        observers.add(observer);
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        if(observer == null) {
            return;
        }
        observers.remove(observer);
    }
}
