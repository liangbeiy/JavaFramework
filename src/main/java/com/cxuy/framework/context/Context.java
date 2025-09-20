/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.context;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.provider.ProviderManager;

public abstract class Context {
    @NonNull
    private final String rootDir;

    @NonNull
    private final ProviderManager providerManager = createProviderManagerInternal();

    protected Context(String root) {
        this.rootDir = root;
    }

    public abstract ClassLoader getClassLoader();

    protected abstract ProviderManager createProviderManager();

    protected abstract FrameworkContext getFrameworkContextInternal();

    public ProviderManager getProviderManager() {
        return providerManager;
    }

    public String getRootDir() {
        return this.rootDir;
    }

    public FrameworkContext getFrameworkContext() {
        if(this instanceof FrameworkContext frameworkContext) {
            return frameworkContext;
        }
        return getFrameworkContextInternal();
    }

    private ProviderManager createProviderManagerInternal() {
        ProviderManager providerManager = createProviderManager();
        return providerManager == null ? new ProviderManager() : providerManager;
    }
}
