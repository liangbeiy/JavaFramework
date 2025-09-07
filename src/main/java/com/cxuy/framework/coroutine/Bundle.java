/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Bundle {
    private final Map<String, Object> elements = Collections.synchronizedMap(new HashMap<>());

    public <T> void putExtra(String key, T element) {
        elements.put(key, element);
    }

    public int getInt(String key, int defaultValue) {
        Object value = getExtra(key);
        if(value instanceof Integer integer) {
            return integer;
        }
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        Object value = getExtra(key);
        if(value instanceof Long longer) {
            return longer;
        }
        return defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        Object value = getExtra(key);
        if(value instanceof Float f) {
            return f;
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getExtra(key);
        if(value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        Object value = getExtra(key);
        if(value instanceof String str) {
            return str;
        }
        return defaultValue;
    }

    public Object getExtra(String key) {
        if(key == null) {
            return null;
        }
        return elements.get(key);
    }

    public void remove(String key) {
        if(key == null) {
            return;
        }
        elements.remove(key);
    }

    public void removeAll() {
        elements.clear();
    }

    public boolean contains(String key) {
        if(key == null) {
            return false;
        }
        return elements.containsKey(key);
    }
}
