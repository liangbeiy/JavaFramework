/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file.execute;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.coroutine.Coroutines;
import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.coroutine.DispatchQueue.Status;
import com.cxuy.framework.coroutine.queue.DispatcherQueue;
import com.cxuy.framework.coroutine.task.Task;
import com.cxuy.framework.io.file.exception.CreateFileException;
import com.cxuy.framework.io.file.exception.LockManagerException;
import com.cxuy.framework.io.file.exception.NotExistsPathException;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileExecutor implements DispatchQueue.StatusObserver {
    private static final String TAG = "FileExecutor";

    @FunctionalInterface
    public interface FileTask {
        void run(String path);
    }

    @FunctionalInterface
    public interface FileExecutorIsEmptyCallback {
        void taskEmptyCallback(FileExecutor executor, String path);
    }

    @FunctionalInterface
    public interface FileTaskCallback {
        void callback(FileExecutor executor, FileTask task, boolean result);
    }

    @FunctionalInterface
    public interface MoveCallback {
        void callback(FileExecutor executor, String oldPath, String newPath, boolean result);
    }

    private final DispatcherQueue worker = new DispatcherQueue();

    private final AtomicInteger shareCount = new AtomicInteger(0);
    private final Object mutexLock = new Object();
    private final AtomicBoolean disallowMutex = new AtomicBoolean(false);

    private final AtomicBoolean pathChangedTaskCommited = new AtomicBoolean(false);
    @NonNull
    private final String path;
    @NonNull
    private final RandomAccessFile accessFile;
    @NonNull
    private final FileChannel channel;

    @Nullable
    private final FileExecutorIsEmptyCallback callback;

    public FileExecutor(String absolutePath, FileExecutorIsEmptyCallback callback) {
        this.path = absolutePath;
        this.callback = callback;
        worker.addObserver(this);

        if(TextUtils.isEmpty(path)) {
            throw new NotExistsPathException();
        }
        File file = new File(path);
        File parentDir = file.getParentFile();

        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new CreateFileException("an error ocurred while creating the parent floder");
        }

        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new CreateFileException("cannot create file, file=" + file.getAbsolutePath());
            }
            this.accessFile = new RandomAccessFile(path, "rw");
            this.channel = accessFile.getChannel();
        } catch (IOException e) {
            LockManagerException throwE = new LockManagerException("cannot create file lock");
            Logger.e(TAG, throwE.getMessage(), throwE);
            Logger.e(TAG, e.getMessage(), e);
            throw throwE;
        }
    }

    /**
     * 文件操作共享
     * <p>
     * 当你需要操作这个文件时，如果你所做的操作可与其他线程共享 可使用此方法增加并发度
     * @param task 操作任务
     */
    public void share(FileTask task) {
        share(task, null);
    }

    public void share(FileTask task, @Nullable FileTaskCallback callback) {
        if(task == null || pathChangedTaskCommited.get()) {
            if(callback != null) {
                callback.callback(this, task, false);
            }
            return;
        }
        worker.async(() -> {
            int count = shareCount.incrementAndGet();
            if(count == 1 && !acquireMutexLock()) {
                shareCount.decrementAndGet();
                if(callback != null) {
                    callback.callback(this, task, false);
                }
                return;
            }
            Coroutines.io.async(() -> execute(task, callback, true, () -> worker.async(() -> {
                int currentCount = shareCount.decrementAndGet();
                if(currentCount == 0) {
                    releaseMutexLock();
                }
            })));
        });
    }

    /**
     * 文件操作互斥
     * <p>
     * 当你需要操作这个文件时，如果你所做的操作不可与其他线程共享 可使用此方法独享文件操作
     * @param task 操作任务
     */
    public void mutex(FileTask task) {
        mutex(task, null);
    }

    public void mutex(FileTask task, @Nullable FileTaskCallback callback) {
        if(task == null || pathChangedTaskCommited.get()) {
            if(callback != null) {
                callback.callback(this, task, false);
            }
            return;
        }
        worker.async(() -> {
            if(!acquireMutexLock()) {
                if(callback != null) {
                    callback.callback(this, task, false);
                }
                return;
            }
            execute(task, callback, false, this::releaseMutexLock);
        });
    }

    public void move(String newPath, boolean overwrite, @NonNull MoveCallback callback) {
        if(TextUtils.isEmpty(newPath) || TextUtils.isEmpty(path) || TextUtils.equals(newPath, path)) {
            callback.callback(this, path, newPath, false);
            return;
        }
        pathChangedTaskCommited.set(true);
        worker.async(() -> {
            if(!acquireMutexLock()) {
                callback.callback(this, path, newPath, false);
                return;
            }
            File oldFile = new File(path);
            File newFile = new File(newPath);
            if(newFile.exists() && !overwrite) {
                releaseMutexLock();
                callback.callback(this, path, newPath, false);
                return;
            }
            boolean moveSuccessful = false;
            try {
                Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                moveSuccessful = true;
            } catch (IOException e) {
                Logger.e(TAG, "an error ocurred while executing move task", e);
            } finally {
                closeExecutor();
                releaseMutexLock();
                callback.callback(this, path, newPath, moveSuccessful);
            }
        });
    }

    public boolean isNoTask() {
        return worker.getStatus().rawValue >= DispatchQueue.Status.IDLE.rawValue;
    }

    @Override
    public void onChanged(DispatcherQueue queue, Status status) {
        if(status.rawValue < DispatchQueue.Status.DORMANT.rawValue || callback == null) {
            return;
        }
        callback.taskEmptyCallback(this, path);
    }

    private boolean acquireMutexLock() {
        synchronized (mutexLock) {
            while(disallowMutex.get()) {
                try {
                    mutexLock.wait();
                } catch (InterruptedException e) {
                    Logger.e(TAG, "an error occurred while waiting mutex lock", e);
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            disallowMutex.set(true);
        }
        return true;
    }

    private void releaseMutexLock() {
        synchronized (mutexLock) {
            disallowMutex.set(false);
            mutexLock.notifyAll();
        }
    }

    private void execute(@NonNull FileTask task, @Nullable FileTaskCallback callback, boolean isShared, @NonNull Runnable finallyBlock) {
        if(!channel.isOpen()) {
            finallyBlock.run();
            if(callback != null) {
                callback.callback(this, task, false);
            }
            return;
        }
        boolean executeSuccessful = false;
        try(FileLock lock = channel.lock(0, Long.MAX_VALUE, isShared)) {
            task.run(path);
            executeSuccessful = true;
        } catch (IOException e) {
            Logger.e(TAG, "an error occurred while trying file lock", e);
        } finally {
            finallyBlock.run();
            if(callback != null) {
                callback.callback(this, task, executeSuccessful);
            }
        }
    }

    public void close() {
        closeExecutor();
    }

    public boolean isClosed() {
        return !channel.isOpen();
    }

    private void closeExecutor() {
        try {
            channel.close();
        } catch (IOException e) {
            Logger.e(TAG, "an exception occurred while closing channel", e);
        }
        try {
            accessFile.close();
        } catch (IOException e) {
            Logger.e(TAG, "an exception occurred while closing accessFile", e);
        }
        worker.shutdown();
    }
}
