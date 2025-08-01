package com.cxuy.framework;

import com.cxuy.framework.util.FileUtil;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;

public class Main {
    public static void main(String[] args) {
        Logger.setLevel(Logger.Level.VERBOSE);
        FileUtil.write("./hello.txt", "hello world", (path, content, mode, result) -> {
            FileUtil.read("./hello.txt", new FileUtil.ReadFileCallback<String>() {
                @Override
                public void callback(String path, String result) {
                    Logger.d("MAIN", result);
                    int[] array = new int[]{0, 1, 2, 3, 4};
                    Logger.d("MAIN", JsonUtil.toJson(array));
                }
            });
        });
    }
}