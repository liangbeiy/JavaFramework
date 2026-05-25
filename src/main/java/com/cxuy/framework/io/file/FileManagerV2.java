/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.io.file.execute.FileExecutor;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class FileManagerV2 implements WriteFileProvider, FileExecutor.FileExecutorIsEmptyCallback {
    private static final String TAG = "FileManager";

    private static class HOLDER {
        private static final FileManagerV2 INSTANCE = new FileManagerV2();
    }

    private final Object transactionLock = new Object();
    private final Map<String, FileExecutor> transaction = new HashMap<>();

    public static FileManagerV2 getInstance() {
        return HOLDER.INSTANCE;
    }

    @Override
    public void write(String path, String content, WriteFileCallback callback) {
        write(path, content, MODE_WRITE, callback, (absolutePath, writeContent, options) -> {
            Files.writeString(Paths.get(absolutePath), writeContent, StandardCharsets.UTF_8, options);
        });
    }

    @Override
    public void write(String path, byte[] bytes, WriteFileCallback callback) {
        write(path, bytes, MODE_WRITE, callback, (absolutePath, writeContent, options) -> {
            Files.write(Paths.get(absolutePath), writeContent, options);
        });
    }

    @Override
    public void append(String path, String content, WriteFileCallback callback) {
        write(path, content, MODE_APPEND, callback, (absolutePath, writeContent, options) -> {
            Files.writeString(Paths.get(absolutePath), writeContent, StandardCharsets.UTF_8, options);
        });
    }

    @Override
    public void append(String path, byte[] bytes, WriteFileCallback callback) {
        write(path, bytes, MODE_APPEND, callback, (absolutePath, writeContent, options) -> {
            Files.write(Paths.get(absolutePath), writeContent, options);
        });
    }

    private StandardOpenOption[] reflectMode(int mode) {
        return mode == MODE_APPEND ? new StandardOpenOption[] { StandardOpenOption.APPEND } :
                new StandardOpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
    }

    private <T> void write(String path, T content, int mode, WriteFileCallback callback, WriteOp<T> op) {
        if(TextUtils.isEmpty(path)) {
            if(callback != null) {
                callback.callback(path, mode, false);
            }
            return;
        }
        String currentAbsolutePath = resolvePath(path);
        getExecutor(currentAbsolutePath).mutex(absolutePath -> {
            // 自动处理文件创建和流关闭，支持追加模式
            boolean writeSuccessful = false;
            try {
                op.operation(absolutePath, content, reflectMode(mode));
                writeSuccessful = true;
            } catch (IOException e) {
                Logger.e(TAG, "an error occurred while writing content", e);
            }
            if(callback != null) {
                callback.callback(absolutePath, mode, writeSuccessful);
            }
        });
    }

    @Override
    public void readString(String path, ReadFileCallback<String> callback) {
        if(TextUtils.isEmpty(path)) {
            if(callback != null) {
                callback.callback(path, false, null);
            }
            return;
        }
        getExecutor(resolvePath(path)).share(absolutePath -> {
            try {
                String content = Files.readString(Paths.get(absolutePath), StandardCharsets.UTF_8);
                callback.callback(path, true, content);
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while reading content, path=" + absolutePath, e);
                callback.callback(path, false, null);
            }
        });
    }

    @Override
    public void readBytes(String path, ReadFileCallback<byte[]> callback) {
        if(TextUtils.isEmpty(path)) {
            if(callback != null) {
                callback.callback(path, false, null);
            }
            return;
        }
        getExecutor(resolvePath(path)).share(absolutePath -> {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(absolutePath));
                callback.callback(path, true, bytes);
            } catch (IOException e) {
                Logger.e(TAG, "an exception occurred while reading content, path=" + absolutePath, e);
                callback.callback(path, false, null);
            }
        });
    }

    @Override
    public boolean isExist(String path) {
        String modifyPath = resolvePath(path);
        File file = new File(modifyPath);
        return file.exists();
    }

    @Override
    public void create(String path) {
        if(TextUtils.isEmpty(path)) {
            return;
        }
        getExecutor(resolvePath(path));
    }

    @Override
    public void delete(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        getExecutor(resolvePath(path)).mutex(absolutePath -> {
            File file = new File(absolutePath);
            if (!file.exists()) {
                return;
            }
            if (file.isFile()) {
                if (!file.delete()) {
                    Logger.w(TAG, "delete file failed, path=" + absolutePath);
                }
            } else if (file.isDirectory()) {
                if (!deleteFolder(file)) {
                    Logger.w(TAG, "delete folder failed, path=" + absolutePath);
                }
            }
        });
    }

    /**
     * 递归删除目录及其子文件
     * @param file 待删除的文件或目录
     * @return 删除成功返回true
     */
    private boolean deleteFolder(File file) {
        if (file.isFile()) {
            return file.delete();
        }
        if (!file.isDirectory()) {
            return true;
        }
        File[] files = file.listFiles();
        if (files == null) {
            return true;
        }
        for (File f : files) {
            if (!deleteFolder(f)) {
                return false;
            }
        }
        return file.delete();
    }

    /**
     * 创建目录（包括必需的父目录）
     * @param path 目录路径
     */
    @Override
    public void mkdirs(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        String absolutePath = resolvePath(path);
        File file = new File(absolutePath);
        if (file.exists()) {
            return;
        }
        if (!file.mkdirs()) {
            Logger.e(TAG, "mkdirs failed, path=" + absolutePath);
        }
    }

    @Override
    public void taskEmptyCallback(FileExecutor executor, String path) {
        synchronized(transactionLock) {
            transaction.remove(path);
        }
        executor.close();
    }

    @NonNull
    private FileExecutor getExecutor(String path) {
        synchronized(transactionLock) {
            FileExecutor executor = transaction.get(path);
            if (executor != null && !executor.isClosed()) {
                return executor;
            }
            executor = new FileExecutor(path, this);
            transaction.put(path, executor);
            return executor;
        }
    }

    @NonNull
    private String resolvePath(@NonNull String path) {
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }

    private interface WriteOp<T> {
        void operation(String absolutePath, T writeContent, StandardOpenOption[] options) throws IOException;
    }
}
