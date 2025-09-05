/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util.io.file;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.io.file.FileExecutor.FileExecutorIsEmptyCallback;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileManager implements FileExecutorIsEmptyCallback {

    @FunctionalInterface
    public interface ReadFileCallback<T> {
        void callback(@NonNull String path, @Nullable T result);
    }

    @FunctionalInterface
    public interface WriteFileCallback<T> {
        int MODE_WRITE = 0;
        int MODE_APPEND = 1;
        void callback(@NonNull String path, @Nullable T content, int mode, boolean result);
    }

    private static final String TAG = "FileUtil";

    private static class HOLDER {
        private static final FileManager INSTANCE = new FileManager();
    }

    private final Object transactionLock = new Object();
    private final Map<String, FileExecutor> transaction = new HashMap<>();

    public static FileManager getInstance() {
        return HOLDER.INSTANCE;
    }

    private FileManager() {
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                Set<String> deleteRemoveItem = new HashSet<>();
                synchronized(transactionLock) {
                    for(Map.Entry<String, FileExecutor> entry : transaction.entrySet()) {
                        if(entry.getValue().taskEmpty()) {
                            deleteRemoveItem.add(entry.getKey());
                        }
                    }
                    for(String item : deleteRemoveItem) {
                        transaction.remove(item);
                    }
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                    // ignored
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 判断当前路径下文件是否存在
     * @param path 给定的文件路径
     * @return 如果存在返回true
     */
    public boolean isExist(@NonNull String path) {
        String modifyPath = resolvePath(path);
        File file = new File(modifyPath);
        return file.exists();
    }

    /**
     * 获取当前工作目录下的绝对路径
     * @return 路径
     */
    public String getAbsolutePath() {
        return System.getProperty("user.dir");
    }

    /**
     * 以String形式读取文件
     * @param path 给定的文件路径
     */
    public void read(@NonNull String path, @NonNull ReadFileCallback<String> callback) {
        String modifyPath = resolvePath(path);
        // 检查文件是否存在
        if (!isExist(modifyPath)) {
            callback.callback(path, null);
        }
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.share(() -> {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(modifyPath));
                callback.callback(path, new String(bytes, StandardCharsets.UTF_8));
            } catch(Exception e) {
                callback.callback(path, null);
            }
        });
    }

    /**
     * 以String形式写入文件
     * @param path 给定的文件路径
     */
    public void write(@NonNull String path, @Nullable String content) {
        write(path, content, WriteFileCallback.MODE_WRITE, null);
    }

    /**
     * 追加文本至文件尾部
     * @param path 文件路径
     * @param content 内容
     */
    public void append(@NonNull String path, @Nullable String content) {
        write(path, content, WriteFileCallback.MODE_APPEND, null);
    }

    /**
     * 以String形式写入文件
     * @param path 给定的文件路径
     */
    public void write(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback<String> callback) {
        write(path, content, WriteFileCallback.MODE_WRITE, callback);
    }

    /**
     * 追加文本至文件尾部
     * @param path 文件路径
     * @param content 内容
     */
    public void append(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback<String> callback) {
        write(path, content, WriteFileCallback.MODE_APPEND, callback);
    }

    private void write(@NonNull String path, @Nullable byte[] content, int mode, @Nullable WriteFileCallback<byte[]> callback) {
        final String modifyPath = resolvePath(path);
        final StandardOpenOption option = reflectMode(mode);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex(() -> {
            File file = new File(modifyPath);
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                Logger.e(TAG, "cannot create folder, folder=" + parentDir.getAbsolutePath());
                if(callback != null) {
                    callback.callback(path, content, mode, false);
                }
                return;
            }
            try {
                if (!file.exists() && !file.createNewFile()) {
                    Logger.e(TAG, "cannot create file, file=" + file.getAbsolutePath());
                    if(callback != null) {
                        callback.callback(path, content, mode, false);
                    }
                    return;
                }
                Files.write(Paths.get(modifyPath), content, StandardOpenOption.CREATE, option);
                if(callback != null) {
                    callback.callback(path, content, mode, true);
                }
            } catch (Exception e) {
                Logger.e(TAG, "write file occurred exception, file=" + file.getAbsolutePath(), e);
                if(callback != null) {
                    callback.callback(path, content, mode, false);
                }
            }
        });
    }

    private void write(@NonNull String path, @Nullable String content, int mode, @Nullable WriteFileCallback<String> callback) {
        final String modifyPath = resolvePath(path);
        final StandardOpenOption option = reflectMode(mode);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex(() -> {
            File file = new File(modifyPath);
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                Logger.e(TAG, "cannot create folder, folder=" + parentDir.getAbsolutePath());
                if(callback != null) {
                    callback.callback(path, content, mode, false);
                }
                return;
            }
            try {
                if (!file.exists() && !file.createNewFile()) {
                    Logger.e(TAG, "cannot create file, file=" + file.getAbsolutePath());
                    if(callback != null) {
                        callback.callback(path, content, mode, false);
                    }
                    return;
                }
                // 自动处理文件创建和流关闭，支持追加模式
                Files.writeString(Paths.get(modifyPath), content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, option);
                if(callback != null) {
                    callback.callback(path, content, mode, true);
                }
            } catch (Exception e) {
                Logger.e(TAG, "write file occurred exception, file=" + file.getAbsolutePath(), e);
                if(callback != null) {
                    callback.callback(path, content, mode, false);
                }
            }
        });
    }

    public void delete(@NonNull String path) {
        final String modifyPath = resolvePath(path);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex(() -> {
            File file = new File(modifyPath);
            if(!file.exists()) {
                return;
            }
            if(file.isFile()) {
                boolean successful = file.delete();
            }
            if(file.isDirectory()) {
                boolean successful = deleteFolder(file);
            }
        });
    }

    private boolean deleteFolder(@NonNull File file) {
        if(file.isFile()) {
            return file.delete();
        }
        if(!file.isDirectory()) { return true; }
        File[] files = file.listFiles();
        if(files == null) {
            return true;
        }
        for(File f : files) {
            if(!deleteFolder(f)) {
                return false;
            }
        }
        return file.delete();
    }

    private StandardOpenOption reflectMode(int mode) {
        return mode == WriteFileCallback.MODE_APPEND ? StandardOpenOption.APPEND : StandardOpenOption.WRITE;
    }

    /**
     * 解析路径，统一处理相对路径和绝对路径
     * 相对路径会基于当前工作目录解析
     * @param path 输入路径（相对或绝对）
     * @return 解析后的Path对象
     */
    private String resolvePath(String path) {
        // Paths.get()会自动处理相对路径和绝对路径
        // 相对路径将基于当前工作目录解析
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }

    @Override
    public void taskEmptyCallback(String path, FileExecutor executor) {
        synchronized(transactionLock) {
            transaction.remove(path);
        }
    }

}