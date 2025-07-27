package com.cxuy.framework.lifecycle;

public interface LifecycleOwner {

    LifecycleState getState();
    void setState(LifecycleState state);

    void addObserver(LifecycleObserver observer);
    void removeObserver(LifecycleObserver observer);
}
