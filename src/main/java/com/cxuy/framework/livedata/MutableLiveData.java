package com.cxuy.framework.livedata;

import com.cxuy.framework.util.DispatcherQueue;

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
