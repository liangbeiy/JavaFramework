/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.provider;

import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProviderManager {
    private static final String TAG = "ProviderManager";
    private final Map<String, Provider> providers = Collections.synchronizedMap(new HashMap<>());

    public void add(String name, Provider provider) {
        if(TextUtils.isEmpty(name) || provider == null) {
            return;
        }
        providers.put(name, provider);
    }

    public <T extends Provider> T get(String name, Class<T> clazz) {
        if(TextUtils.isEmpty(name)) {
            return null;
        }
        Provider provider = providers.get(name);
        try {
            return clazz.cast(provider);
        } catch (ClassCastException e) {
            Logger.d(TAG, "provider cannot convert to class=" + clazz.getName());
            return null;
        }
    }

    public void remove(String name) {
        if(TextUtils.isEmpty(name)) {
            return;
        }
        providers.remove(name);
    }
}
