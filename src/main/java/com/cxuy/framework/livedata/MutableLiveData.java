/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.livedata;

import com.cxuy.framework.coroutine.DispatcherQueue;

public class MutableLiveData<T> extends LiveData<T> {

    public MutableLiveData() {
        super(null);
        version = -1;
    }

    public MutableLiveData(T data) {
        super(data);
    }

    public void post(T data) {
        DispatcherQueue.standard.async(() -> {
            this.data = data;
            version++;
            dispatch(data, version);
        });
    }
}
