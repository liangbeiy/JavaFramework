package com.cxuy.framework.util;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileUtil {

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

    /**
     * 判断当前路径下文件是否存在
     * @param path 给定的文件路径
     * @return 如果存在返回true
     */
    public static boolean isExist(@NonNull String path) {
        String modifyPath = resolvePath(path);
        File file = new File(modifyPath);
        return file.exists();
    }

    /**
     * 获取当前工作目录下的绝对路径
     * @return 路径
     */
    public static String getAbsolutePath() {
        return System.getProperty("user.dir");
    }

    /**
     * 以String形式读取文件
     * @param path 给定的文件路径
     */
    public static void read(@NonNull String path, @NonNull ReadFileCallback<String> callback) {
        String modifyPath = resolvePath(path);
        // 检查文件是否存在
        if (!isExist(modifyPath)) {
            callback.callback(path, null);
        }
        DispatcherQueue.io.async(() -> {
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
    public static void write(@NonNull String path, @Nullable String content) {
        write(path, content, WriteFileCallback.MODE_WRITE, null);
    }

    /**
     * 追加文本至文件尾部
     * @param path 文件路径
     * @param content 内容
     */
    public static void append(@NonNull String path, @Nullable String content) {
        write(path, content, WriteFileCallback.MODE_APPEND, null);
    }

    /**
     * 以String形式写入文件
     * @param path 给定的文件路径
     */
    public static void write(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback<String> callback) {
        write(path, content, WriteFileCallback.MODE_WRITE, callback);
    }

    /**
     * 追加文本至文件尾部
     * @param path 文件路径
     * @param content 内容
     */
    public static void append(@NonNull String path, @Nullable String content, @Nullable WriteFileCallback<String> callback) {
        write(path, content, WriteFileCallback.MODE_APPEND, callback);
    }

    private static void write(@NonNull String path, @Nullable byte[] content, int mode, @Nullable WriteFileCallback<byte[]> callback) {
        final String modifyPath = resolvePath(path);
        final StandardOpenOption option = reflectMode(mode);
        DispatcherQueue.io.async(() -> {
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

    private static void write(@NonNull String path, @Nullable String content, int mode, @Nullable WriteFileCallback<String> callback) {
        final String modifyPath = resolvePath(path);
        final StandardOpenOption option = reflectMode(mode);
        DispatcherQueue.io.async(() -> {
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
                // 使用NIO的Files.write简化追加操作
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

    private static StandardOpenOption reflectMode(int mode) {
        return switch(mode) {
            case WriteFileCallback.MODE_WRITE -> StandardOpenOption.WRITE;
            case WriteFileCallback.MODE_APPEND -> StandardOpenOption.APPEND;
            default -> StandardOpenOption.WRITE;
        };
    }

    /**
     * 解析路径，统一处理相对路径和绝对路径
     * 相对路径会基于当前工作目录解析
     * @param path 输入路径（相对或绝对）
     * @return 解析后的Path对象
     */
        private static String resolvePath(String path) {
        // Paths.get()会自动处理相对路径和绝对路径
        // 相对路径将基于当前工作目录解析
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }
}
