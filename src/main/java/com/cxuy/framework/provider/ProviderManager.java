/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.provider;

import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProviderManager {
    private static final String TAG = "ProviderManager";
    private final Map<String, Provider> providers = Collections.synchronizedMap(new HashMap<>());

    public void add(String name, Provider provider) {
        if(TextUtil.isEmpty(name) || provider == null) {
            return;
        }
        providers.put(name, provider);
    }

    public <T extends Provider> T get(String name, Class<T> clazz) {
        if(TextUtil.isEmpty(name)) {
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
        if(TextUtil.isEmpty(name)) {
            return;
        }
        providers.remove(name);
    }
}
