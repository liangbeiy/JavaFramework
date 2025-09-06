/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.context;

import com.cxuy.framework.annotation.NonNull;

public abstract class Context {

    @NonNull
    private final String rootDir;

    public Context(String root) {
        this.rootDir = root;
    }

    public String getRootDir() {
        return this.rootDir;
    }

    public FrameworkContext getFrameworkContext() {
        if(this instanceof FrameworkContext frameworkContext) {
            return frameworkContext;
        }
        return null;
    }
}
