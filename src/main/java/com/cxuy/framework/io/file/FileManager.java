/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.io.file.exception.CreateFileException;
import com.cxuy.framework.io.file.exception.ParentFileException;
import com.cxuy.framework.io.file.execute.FileExecutor;
import com.cxuy.framework.io.file.execute.FileExecutor.FileExecutorIsEmptyCallback;
import com.cxuy.framework.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件管理器（旧版）。
 * 提供基于 FileExecutor 的文件读写、删除等操作。
 *
 * @deprecated 请使用 {@link FileManagerV2} 替代。
 *             FileManager 将在后续版本中移除。
 */
@Deprecated
public class FileManager implements FileExecutorIsEmptyCallback {
    private static final String TAG = "FileManager";

    @FunctionalInterface
    public interface ReadFileCallback<T> {
        void callback(@NonNull String path, boolean result, @Nullable T content);
    }

    @FunctionalInterface
    public interface WriteFileCallback<T> {
        int MODE_WRITE = 0;
        int MODE_APPEND = 1;
        void callback(@NonNull String path, @Nullable T content, int mode, boolean result);
    }

    private static class HOLDER {
        private static final FileManager INSTANCE = new FileManager();
    }

    private final Object transactionLock = new Object();
    private final Map<String, FileExecutor> transaction = new HashMap<>();

    public static FileManager getInstance() {
        return HOLDER.INSTANCE;
    }

    private FileManager() {  }

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

    public void createFile(String path) throws ParentFileException, CreateFileException {
        String modifyPath = resolvePath(path);
        if(isExist(modifyPath)) {
            return;
        }
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex((p) -> {
            File file = new File(modifyPath);
            if(file.exists()) {
                return;
            }
            File parentDir = file.getParentFile();
            if(parentDir != null && !parentDir.exists()) {
                boolean success = parentDir.mkdirs();
                if(!success) {
                    throw new ParentFileException("parent dir not exists");
                }
            }
            try {
                boolean success = file.createNewFile();
                if(!success) {
                    throw new CreateFileException("cannot create file whose path is " + path);
                }
            } catch (IOException _) {
                throw new CreateFileException("cannot create file whose path is " + path);
            }
        });
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
        if (!isExist(modifyPath)) {
            callback.callback(path, false, null);
            return;
        }
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.share((p) -> {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(modifyPath));
                callback.callback(path, true, new String(bytes, StandardCharsets.UTF_8));
            } catch(Exception e) {
                callback.callback(path, false, null);
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

    /**
     * 删除一个文件夹或文件
     * @param path 路径
     */
    public void delete(@NonNull String path) {
        final String modifyPath = resolvePath(path);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, _ -> new FileExecutor(path, this));
        }
        executor.mutex((p) -> {
            File file = new File(modifyPath);
            if(!file.exists()) {
                return;
            }
            if(file.isFile() && !file.delete()) {
                Logger.w(TAG, "an exception occurred while deleting file, path=" + file.getAbsolutePath());
            }
            if(file.isDirectory() && !deleteFolder(file)) {
                Logger.w(TAG, "an exception occurred while deleting folder, path=" + file.getAbsolutePath());
            }
        });
    }

    private void write(@NonNull String path, @Nullable byte[] content, int mode, @Nullable WriteFileCallback<byte[]> callback) {
        final String modifyPath = resolvePath(path);
        final StandardOpenOption[] options = reflectMode(mode);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex((p) -> {
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
                Files.write(Paths.get(modifyPath), content, options);
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
        final StandardOpenOption[] options = reflectMode(mode);
        FileExecutor executor;
        synchronized(transactionLock) {
            executor = transaction.computeIfAbsent(modifyPath, s -> new FileExecutor(path, this));
        }
        executor.mutex((p) -> {
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
                Files.writeString(Paths.get(modifyPath), content, StandardCharsets.UTF_8, options);
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

    private StandardOpenOption[] reflectMode(int mode) {
        return mode == WriteFileCallback.MODE_APPEND ?
                new StandardOpenOption[] { StandardOpenOption.APPEND } :
                new StandardOpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
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
    public void taskEmptyCallback(FileExecutor executor, String path) {
        synchronized(transactionLock) {
            transaction.remove(path);
        }
        executor.close();
    }

}