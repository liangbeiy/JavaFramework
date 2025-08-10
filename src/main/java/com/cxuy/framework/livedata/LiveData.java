/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.livedata;

import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.util.DispatcherQueue;

import java.util.HashMap;
import java.util.Map;

public class LiveData<T> {

    @FunctionalInterface
    public interface LivaDataOnChanged<T> {
        void onChanged(T data); 
    }

    protected T data; 
    protected int version;

    public LiveData(T data) {
        this.data = data;
        version = 0;
    }

    private Map<LifecycleOwnerWrapper, LivaDataOnChanged<T>> observers = new HashMap<>();

    public void observe(LifecycleOwner owner, LivaDataOnChanged<T> function) {
        if(owner == null) {
            return;
        }
        DispatcherQueue.standard.async(() -> {
            LifecycleOwnerWrapper wrapper = null;
            for(LifecycleOwnerWrapper w : observers.keySet()) {
                if(owner == w.owner) {
                    wrapper = w;
                }
            }
            if(wrapper != null) {
                observers.remove(wrapper);
            }
            observers.put(new LifecycleOwnerWrapper(owner), function);
            dispatch(data, version);
        });
    }

    protected void dispatch(T data, int version) {
        for(Map.Entry<LifecycleOwnerWrapper, LivaDataOnChanged<T>> entry : observers.entrySet()) {
            LifecycleOwnerWrapper wrapper = entry.getKey();
            LivaDataOnChanged<T> function = entry.getValue();
            if(wrapper.getVersion() < version) {
                function.onChanged(data);
                wrapper.setVersion(version);
            }
        }
    }

    private static class LifecycleOwnerWrapper {
        public final LifecycleOwner owner;
        private int version;

        public LifecycleOwnerWrapper(LifecycleOwner owner) {
            this.owner = owner;
            this.version = -1;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }
    }
}
