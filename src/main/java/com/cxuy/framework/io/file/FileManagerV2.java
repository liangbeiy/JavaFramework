/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.io.file;

public class FileManagerV2 implements FileProvider {

    private static final class HOLDER {
        private static final FileManagerV2 INSTANCE = new FileManagerV2();
    }

    private boolean multiProcessEnable = false;

    public static FileManagerV2 getInstance() {
        return HOLDER.INSTANCE;
    }

    public void isMultiProcess(boolean enable) {
        this.multiProcessEnable = enable;
    }

    @Override
    public boolean isExist(String path) {
        return false;
    }

    @Override
    public void create(String path) {

    }

    @Override
    public void readString(String path, ReadFileCallback<String> callback) {

    }

    @Override
    public void readBytes(String path, ReadFileCallback<byte[]> callback) {

    }

    @Override
    public void write(String path, String content, WriteFileCallback callback) {

    }

    @Override
    public void write(String path, byte[] bytes, WriteFileCallback callback) {

    }

    @Override
    public void append(String path, String content, WriteFileCallback callback) {

    }

    @Override
    public void append(String path, byte[] bytes, WriteFileCallback callback) {

    }

    @Override
    public void delete(String path) {

    }

    @Override
    public void mkdir(String path) {

    }

    @Override
    public void copy(String sourcePath, String destinationPath) {

    }

    @Override
    public void move(String sourcePath, String destinationPath) {

    }
}