/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import java.util.concurrent.Semaphore;

import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.coroutine.DispatchQueue.Status;
import com.cxuy.framework.util.Logger;

public class FileExecutor implements DispatchQueue.StatusObserver {
    public interface FileExecutorIsEmptyCallback {
        void taskEmptyCallback(String path, FileExecutor executor);
    }

    private static final String TAG = "FileExecutor";

    private final DispatchQueue worker = new DispatchQueue();

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

    /**
     * 文件操作共享
     * <p>
     * 当你需要操作这个文件时，如果你所做的操作可与其他线程共享 可使用此方法增加并发度
     * @param task 操作任务
     */
    public void share(DispatchQueue.Task task) {
        worker.async((workerContext) -> {
            try {
                readMutex.acquire();
                readerCount++;
                if(readerCount == 1) { // 第一个读者拦截写操作
                    writeMutex.acquire();
                }
                readMutex.release();

                DispatchQueue.io.async((context) -> {
                    task.run(context);
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
    /**
     * 文件操作互斥
     * <p>
     * 当你需要操作这个文件时，如果你所做的操作不可与其他线程共享 可使用此方法独享文件操作
     * @param task 操作任务
     */
    public void mutex(DispatchQueue.Task task) {
        worker.async((context) -> {
            try {
                writeMutex.acquire();
                task.run(context);
                writeMutex.release();
            } catch(InterruptedException e) {
                Logger.e(TAG, "Acquiring Semaphore failure. ");
            }
        });
    }

    public boolean taskEmpty() {
        return worker.getStatus().rawValue >= DispatchQueue.Status.IDLE.rawValue;
    }

    @Override
    public void onChanged(DispatchQueue queue, Status status) {
        if(callback != null && status.rawValue >= DispatchQueue.Status.DORMANT.rawValue) {
            callback.taskEmptyCallback(path, this);
        }
    }
}
