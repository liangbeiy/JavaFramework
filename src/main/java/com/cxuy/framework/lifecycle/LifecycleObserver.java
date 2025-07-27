package com.cxuy.framework.lifecycle;

public interface LifecycleObserver {
    void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state); 
}
