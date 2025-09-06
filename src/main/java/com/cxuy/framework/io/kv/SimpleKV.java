/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.kv;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.context.Context;
import com.cxuy.framework.context.FrameworkContext;
import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.TextUtil;
import com.cxuy.framework.io.file.FileManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleKV implements MapStorage, LifecycleObserver {
    private static final String SUB_PATH = "simple_kv";
    private static final String DEFAULT_NAME = "SimpleKV_default";
    private static final int MAX_WRITE_TO_DISK_COUNT = 10;

    private final FrameworkContext context;
    private final String storagePath;

    private final Map<String, String> kvMap = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> pendingRemoveKey = Collections.synchronizedSet(new HashSet<>());

    private final Object writeLock = new Object();
    private int writeCount = 0;

    private final Object initLock = new Object();
    private final AtomicBoolean isInit = new AtomicBoolean(false);
    public SimpleKV(Context context) {
        this(context, DEFAULT_NAME);
    }

    public SimpleKV(Context context, String name) {
        this.context = context.getFrameworkContext();
        String diskPath = context.getRootDir();
        String path = diskPath + File.separator + SUB_PATH + File.separator + name + ".kv";
        storagePath = path;
        this.context.addObserver(this);
        if(!FileManager.getInstance().isExist(path)) {
            FileManager.getInstance().createFile(path);
        }
        FileManager.getInstance().read(storagePath, (jsonPath, result) -> {
            if(TextUtil.isEmpty(result)) {
                synchronized(initLock) {
                    isInit.set(true);
                    initLock.notifyAll();
                }
                return;
            }
            Map<String, String> fileMap = JsonUtil.fromJson(result, Map.class);
            if(fileMap == null) {
                synchronized(initLock) {
                    isInit.set(true);
                    initLock.notifyAll();
                }
                return;
            }
            for(Map.Entry<String, String> entry : fileMap.entrySet()) {
                if(kvMap.containsKey(entry.getKey()) || pendingRemoveKey.contains(entry.getKey())) {
                    continue;
                }
                kvMap.put(entry.getKey(), entry.getValue());
            }
            synchronized(initLock) {
                isInit.set(true);
                initLock.notifyAll();
            }
        });
    }

    public SimpleKV putInt(@Nullable String key, int value) {
        String modifyValue = String.valueOf(value);
        put(key, modifyValue);
        return this;
    }

    public SimpleKV putLong(@Nullable String key, long value) {
        String modifyValue = String.valueOf(value);
        put(key, modifyValue);
        return this;
    }

    public SimpleKV putFloat(@Nullable String key, float value) {
        String modifyValue = String.valueOf(value);
        put(key, modifyValue);
        return this;
    }

    public SimpleKV putString(@Nullable String key, String value) {
        put(key, value);
        return this;
    }

    public SimpleKV putArray(@Nullable String key, String[] value) {
        String json = JsonUtil.toJson(value);
        put(key, json);
        return this;
    }

    public SimpleKV putList(@Nullable String key, List<String> value) {
        String json = JsonUtil.toJson(value);
        put(key, json);
        return this;
    }

    public SimpleKV putSet(@Nullable String key, Set<String> value) {
        String json = JsonUtil.toJson(value);
        put(key, json);
        return this;
    }

    public SimpleKV putMap(@Nullable String key, Map<String, String> value) {
        String json = JsonUtil.toJson(value);
        put(key, json);
        return this;
    }

    @Override
    public void put(@Nullable String key, @Nullable String value) {
        if(TextUtil.isEmpty(key) || value == null) {
            return;
        }
        kvMap.put(key, value);
        recordApply();
    }

    public SimpleKV delete(String key) {
        remove(key);
        return this;
    }

    @Override
    public void remove(String key) {
        if(TextUtil.isEmpty(key)) {
            return;
        }
        kvMap.remove(key);
        synchronized (initLock) {
            if(!isInit.get()) {
                pendingRemoveKey.add(key);
            }
        }
        boolean success = waitForInit();
        if(!success) {
            return;
        }
        recordApply();
    }

    public SimpleKV deleteAll() {
        removeAll();
        return this;
    }

    @Override
    public void removeAll() {
        synchronized (initLock) {
            if(!isInit.get()) {
                for(Map.Entry<String, String> entry : kvMap.entrySet()) {
                    pendingRemoveKey.add(entry.getKey());
                }
            }
        }
        kvMap.clear();
        boolean success = waitForInit();
        if(!success) {
            return;
        }
        apply();
    }

    public int getInt(@Nullable String key, int defaultValue) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(@Nullable String key, long defaultValue) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public float getFloat(@Nullable String key, float defaultValue) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getString(@Nullable String key, String defaultValue) {
        return get(key, defaultValue);
    }

    @Nullable
    public String[] getArray(@Nullable String key) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return null;
        }
        return JsonUtil.fromJsonToArray(value, String[].class);
    }

    @Nullable
    public List<String> getList(@Nullable String key) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return null;
        }
        return JsonUtil.fromJsonToList(value, String.class);
    }

    @Nullable
    public Set<String> getSet(@Nullable String key) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return null;
        }
        return JsonUtil.fromJson(value, Set.class, String.class);
    }

    public Map<String, String> getMap(@Nullable String key) {
        String value = get(key, null);
        if(TextUtil.isEmpty(value)) {
            return null;
        }
        return JsonUtil.fromJson(value, Map.class, String.class, String.class);
    }

    @Override
    public String get(String key, String defaultValue) {
        if(TextUtil.isEmpty(key)) {
            return null;
        }
        boolean success = waitForInit();
        if(!success) {
            return null;
        }
        String value = kvMap.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public boolean contains(String key) {
        if(TextUtil.isEmpty(key)) {
            return false;
        }
        boolean success = waitForInit();
        if(!success) {
            return false;
        }
        return kvMap.containsKey(key);
    }

    @Override
    public void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state) {
        if(owner == context && state == LifecycleState.DID_DESTROY) {
            FileManager.getInstance().write(storagePath, JsonUtil.toJson(kvMap), null);
        }
    }

    public void apply() {
        synchronized(writeLock) {
            if(writeCount == 0) {
                return;
            }
            FileManager.getInstance().write(storagePath, JsonUtil.toJson(kvMap), null);
            writeCount = 0;
        }
    }

    private void recordApply() {
        boolean applyChange;
        synchronized(writeLock) {
            writeCount++;
            applyChange = writeCount == MAX_WRITE_TO_DISK_COUNT;
        }
        if(applyChange) {
            apply();
        }
    }

    private boolean waitForInit() {
        synchronized(initLock) {
            while(!isInit.get()) {
                try {
                    initLock.wait();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }
}
