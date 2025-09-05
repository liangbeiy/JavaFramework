/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util.io.file;

import java.util.concurrent.Semaphore;

import com.cxuy.framework.coroutine.DispatcherQueue;
import com.cxuy.framework.coroutine.DispatcherQueue.Status;
import com.cxuy.framework.util.Logger;

public class FileExecutor implements DispatcherQueue.StatusObserver {
    public interface FileExecutorIsEmptyCallback {
        void taskEmptyCallback(String path, FileExecutor executor);
    }

    private static final String TAG = "FileExecutor";

    private final DispatcherQueue worker = new DispatcherQueue();

    private final Semaphore readMutex = new Semaphore(1);
    private int readerCount = 0;
    private final Semaphore writeMutex = new Semaphore(1);

    private final String path;
    private final FileExecutorIsEmptyCallback callback;

    public FileExecutor(String path, FileExecutorIsEmptyCallback callback) {
        this.path = path;
        this.callback = callback;
        worker.addStatusObserver(this);
    }

    public void share(DispatcherQueue.Task task) {
        worker.async(() -> {
            try {
                readMutex.acquire();
                readerCount++;
                if(readerCount == 1) { // 第一个读者拦截写操作
                    writeMutex.acquire();
                }
                readMutex.release();

                DispatcherQueue.io.async(() -> {
                    task.run();

                    // 开始释放信号量
                    try {
                        readMutex.acquire();
                        readerCount--;
                        if(readerCount == 0) {
                            writeMutex.release();
                        }
                        readMutex.release();
                    } catch(InterruptedException e) {
                        Logger.e(TAG, "Acquiring Semaphore failure. ");
                    }
                });
            } catch(InterruptedException e) {
                Logger.e(TAG, "Acquiring Semaphore failure. ");
            }
        });
    }

    public void mutex(DispatcherQueue.Task task) {
        worker.async(() -> {
            try {
                writeMutex.acquire();
                task.run();
                writeMutex.release();
            } catch(InterruptedException e) {
                Logger.e(TAG, "Acquiring Semaphore failure. ");
            }
        });
    }

    public boolean taskEmpty() {
        return worker.getStatus().rawValue >= DispatcherQueue.Status.IDLE.rawValue;
    }

    @Override
    public void onChanged(DispatcherQueue queue, Status status) {
        if(callback != null && status.rawValue >= DispatcherQueue.Status.DORMANT.rawValue) {
            callback.taskEmptyCallback(path, this);
        }
    }
}
