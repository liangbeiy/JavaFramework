/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.kv;

import com.cxuy.framework.provider.Provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KVProvider implements Provider {
    public final Map<String, MapStorage> mapStorage = Collections.synchronizedMap(new HashMap<>());

//    public <T extends MapStorage> get(String name, boolean multiProcess) {
//
//    }
}
