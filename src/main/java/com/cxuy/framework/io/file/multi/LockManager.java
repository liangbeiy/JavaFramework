/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file.multi;

import com.cxuy.framework.context.Context;
import com.cxuy.framework.io.file.exception.LockManagerException;
import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LockManager implements LockService, LifecycleOwner, LifecycleObserver {

    private static final String TAG = "LockManager";

    private static final AtomicReference<LockManager> INSTANCE = new AtomicReference<>();
    public static LockManager getInstance(Context context) {
        if(INSTANCE.get() == null) {
            synchronized(LockManager.class) {
                if(INSTANCE.get() == null) {
                    INSTANCE.set(new LockManager(context));
                }
            }
        }
        return INSTANCE.get();
    }

    private WatchService service;

    /**父目录绝对路径 -> RecordItem*/
    private final Map<String, RecordItem> dirToRecordMap = Collections.synchronizedMap(new HashMap<>());
    /**WatchKey -> RecordItem*/
    private final Map<WatchKey, RecordItem> watchKeyToRecordMap = Collections.synchronizedMap(new HashMap<>());
    /**绝对路径 -> Lock*/
    private final Map<String, Lock> fileLockMap = Collections.synchronizedMap(new HashMap<>());

    private final Object stateLock = new Object();
    private LifecycleState state;
    private final Set<LifecycleObserver> observers = Collections.synchronizedSet(new HashSet<>());

    private LockManager(Context context) {
        if(context == null) {
            throw new LockManagerException("an exception occurred while initializing " + TAG);
        }
        context.getFrameworkContext().addObserver(this);
        try {
            setState(LifecycleState.WILL_CREATE);
            service = FileSystems.getDefault().newWatchService();
            Thread keyListener = new Thread(this::watch, "LockManager#worker");
            keyListener.start();
            setState(LifecycleState.DID_CREATE);
        } catch (IOException e) {
            service = null;
            setState(LifecycleState.DID_DESTROY);
            Logger.e(TAG, "an exception occurred while adding watch service", e);
            Logger.w(TAG, "an exception occurred while adding watch service, work thread was not started");
        }
    }

    @Override
    public void acquire(String filePath, LockCallback callback) {
        if(TextUtil.isEmpty(filePath) || callback == null) {
            throw new IllegalArgumentException("filePath or callback is null");
        }
        if(!(LifecycleState.DID_CREATE.rawValue <= getState().rawValue && getState().rawValue < LifecycleState.WILL_DESTROY.rawValue)) {
            callback.callback(filePath, false);
            return;
        }
        if(tryAcquireLock(filePath)) {
            callback.callback(filePath, true);
            return;
        }
        // 获取锁失败，注册监听文件状态
        synchronized(stateLock) {
            if(!(LifecycleState.DID_CREATE.rawValue <= state.rawValue && state.rawValue < LifecycleState.WILL_DESTROY.rawValue)) {
                callback.callback(filePath, false);
                return;
            }
            try {
                Path path = Paths.get(filePath);
                Path parentDir = path.getParent();
                if(parentDir == null) {
                    callback.callback(filePath, false);
                    return;
                }
                WatchKey watchKey = parentDir.register(service,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE);
                putRecord(filePath, watchKey);
            } catch (IOException | InvalidPathException e) {
                Logger.e(TAG, "acquiring lock has fail", e);
                callback.callback(filePath, false);
            }
        }
    }

    @Override
    public void release(String filePath) {
        if(getState().rawValue < LifecycleState.DID_CREATE.rawValue) {
            return;
        }
        Lock mapLock = fileLockMap.get(filePath);
        if(mapLock == null) {
            return;
        }
        mapLock.decrease();
        if(mapLock.count > 0) {
            return;
        }
        Lock lock = fileLockMap.remove(filePath);
        if(lock == null) {
            return;
        }
        try {
            if(lock.fileLock != null && lock.fileLock.isValid()) {
                lock.fileLock.release();
            }
            if(lock.channel != null && lock.channel.isOpen()) {
                lock.channel.close();
            }
        } catch (IOException e) {
            Logger.e(TAG, "release the lock occurred error", e);
        }
        removeRecord(filePath);
    }

    public void releaseAll() {
        if(getState().rawValue < LifecycleState.DID_CREATE.rawValue) {
            return;
        }
        Iterator<Map.Entry<String, Lock>> lockIterator = fileLockMap.entrySet().iterator();
        while (lockIterator.hasNext()) {
            Map.Entry<String, Lock> entry = lockIterator.next();
            Lock lock = entry.getValue();
            try {
                if (lock.fileLock != null && lock.fileLock.isValid()) {
                    lock.fileLock.release();
                }
                if (lock.channel != null && lock.channel.isOpen()) {
                    lock.channel.close();
                }
            } catch (IOException e) {
                Logger.e(TAG, "Failed to release lock in releaseAll: " + entry.getKey(), e);
            }
            lockIterator.remove(); // 移除已处理的锁记录
        }

        dirToRecordMap.clear();
        watchKeyToRecordMap.clear();
    }

    @Override
    public void lifecycleOnChanged(LifecycleOwner owner, LifecycleState state) {
        if(state.rawValue >= LifecycleState.WILL_DESTROY.rawValue) {
            setState(LifecycleState.WILL_DESTROY);
            if (service != null) {
                try {
                    service.close();
                } catch (IOException e) {
                    Logger.e(TAG, "Failed to close WatchService in releaseAll", e);
                }
                service = null;
            }
            releaseAll();
            setState(LifecycleState.DID_DESTROY);
        }
    }

    private void watch() {
        if(service == null) {
            Logger.w(TAG, "service is null pointer, check!!!");
            Thread.currentThread().interrupt();
            return;
        }
        while(!Thread.currentThread().isInterrupted()) {
            if(getState().rawValue >= LifecycleState.WILL_DESTROY.rawValue) {
                return;
            }
            try {
                WatchKey currentKey = service.poll(1, TimeUnit.SECONDS);
                if(currentKey == null) {
                    continue;
                }
                for(WatchEvent<?> event : currentKey.pollEvents()) {
                    handleWatchEvent(currentKey, event);
                }
                // watch 被取消、删除、不支持监听了，取消watch
                if(!currentKey.reset()) {
                    currentKey.cancel();
                    RecordItem recordItem = watchKeyToRecordMap.get(currentKey);
                    if(recordItem != null) {
                        dirToRecordMap.remove(recordItem.watchPath);
                        watchKeyToRecordMap.remove(currentKey);
                    }
                }
            } catch (ClosedWatchServiceException | InterruptedException e) {
                Logger.e(TAG, "watch thread has been interrupted", e);
                // re-interrupt
                Thread.currentThread().interrupt();
            }

        }
        Logger.d(TAG, "watching task is finished");
    }

    private boolean tryAcquireLock(String filePath) {
        Lock mapLock = fileLockMap.get(filePath);
        if(mapLock != null && mapLock.fileLock.isValid()) {
            mapLock.increase();
            return true;
        }
        if(mapLock != null) {
            fileLockMap.remove(filePath);
        }
        FileChannel channel = null;
        try {
            Path path = Paths.get(filePath);
            channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            // 尝试获取独占锁
            FileLock lock = channel.tryLock();
            if (lock != null) {
                fileLockMap.put(filePath, new Lock(channel, lock));
                return true;
            }
            return false;
        } catch (IOException e) {
            Logger.e(TAG, "acquiring lock fail" + filePath, e);
            if(channel != null) {
                try {
                    channel.close();
                } catch (IOException ex) {
                    Logger.e(TAG, "acquire lock failure and closing channel occurred exception", ex);
                }
            }
            return false;
        }
    }

    private void handleWatchEvent(WatchKey currentKey, WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();
        if(kind == StandardWatchEventKinds.OVERFLOW) {
            return;
        }
        Object watchContext = event.context();
        if(!(watchContext instanceof Path path)) {
            Logger.w(TAG, "watch event's context isn't Path");
            return;
        }
        RecordItem recordItem = watchKeyToRecordMap.get(currentKey);
        String fileName = path.getFileName().toString();
        if (recordItem == null || TextUtil.isEmpty(fileName)) {
            return;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            String absolutePath = recordItem.watchPath + File.separator + fileName;
            // 移除锁缓存
            fileLockMap.remove(absolutePath);
            // 从监听列表中移除
            recordItem.unregister(absolutePath);
            // 若该目录无监听文件，取消WatchKey
            if (recordItem.watchFileName.isEmpty()) {
                watchKeyToRecordMap.remove(currentKey);
                currentKey.cancel();
                dirToRecordMap.remove(recordItem.watchPath);
            }
            return;
        }
        String absolutePath = recordItem.watchPath + File.separator + fileName;
        if(recordItem.isWatch(absolutePath)) {
            tryAcquireLock(absolutePath);
        }
    }

    private void putRecord(String filePath, WatchKey key) {
        if(TextUtil.isEmpty(filePath) || key == null) {
            return;
        }
        Path parentDir = Paths.get(filePath).getParent();
        String fileName = Paths.get(filePath).getFileName().toString();
        if(parentDir == null || TextUtil.isEmpty(fileName)) {
            return;
        }
        RecordItem recordItem;
        if(dirToRecordMap.containsKey(parentDir.toString())) {
            recordItem = dirToRecordMap.get(parentDir.toString());
        } else {
            recordItem = new RecordItem(key, parentDir.toString());
            dirToRecordMap.put(parentDir.toString(), recordItem);
            watchKeyToRecordMap.put(key, recordItem);
        }
        boolean result = recordItem.register(filePath);
        if(!result) {
            Logger.w(TAG, "register watch service is failure, check!!!");
        }
    }

    private void removeRecord(String filePath) {
        if(TextUtil.isEmpty(filePath)) {
            return;
        }
        Path absPath = Paths.get(filePath);
        Path parentDir = absPath.getParent();
        if(parentDir == null) {
            return;
        }
        RecordItem recordItem = dirToRecordMap.remove(parentDir.toString());
        if(recordItem == null) {
            return;
        }
        recordItem.unregister(filePath);
        if(recordItem.watchFileName.isEmpty()) { // 关闭 watch
            watchKeyToRecordMap.remove(recordItem.watchKey);
            recordItem.watchKey.cancel();
        }
    }

    @Override
    public LifecycleState getState() {
        LifecycleState result;
        synchronized(stateLock) {
            result = state;
        }
        return result;
    }

    @Override
    public void setState(LifecycleState state) {
        synchronized(stateLock) {
            this.state = state;
        }
        for(LifecycleObserver observer : observers) {
            observer.lifecycleOnChanged(this, state);
        }
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        if(observer == null) {
            return;
        }
        observers.add(observer);
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        if(observer == null) {
            return;
        }
        observers.remove(observer);
    }

    private static class RecordItem {
        public final WatchKey watchKey;
        public final String watchPath;
        public final Set<String> watchFileName;

        public RecordItem(WatchKey key, String parentDir) {
            this.watchKey = key;
            this.watchPath = parentDir;
            this.watchFileName = new HashSet<>();
        }

        public boolean register(String absolutePath) {
            Path path = Paths.get(absolutePath);
            String fileName = path.getFileName().toString();
            String parentDir = path.getParent().toString();
            if(!TextUtil.equals(watchPath, parentDir)) {
                return false;
            }
            watchFileName.add(fileName);
            return true;
        }

        public boolean unregister(String absolutePath) {
            Path path = Paths.get(absolutePath);
            String fileName = path.getFileName().toString();
            String parentDir = path.getParent().toString();
            if(!TextUtil.equals(watchPath, parentDir)) {
                return false;
            }
            watchFileName.remove(fileName);
            return true;
        }

        public boolean isWatch(String absolutePath) {
            Path path = Paths.get(absolutePath);
            String fileName = path.getFileName().toString();
            String parentDir = path.getParent().toString();
            return TextUtil.equals(watchPath, parentDir) && watchFileName.contains(fileName);
        }
    }

    private static class Lock {
        public final FileChannel channel;
        public final FileLock fileLock;
        private final Object countLock = new Object();
        private int count;
        Lock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.fileLock = lock;
            count = 1;
        }

        public void increase() {
            synchronized(countLock) {
                count++;
            }
        }

        public void decrease() {
            synchronized(countLock) {
                count--;
            }
        }

        public boolean releaseEnable() {
            boolean enable;
            synchronized(countLock) {
                enable = count == 0;
            }
            return enable;
        }
    }
}
