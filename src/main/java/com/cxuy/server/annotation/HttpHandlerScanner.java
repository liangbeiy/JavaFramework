/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.server.annotation;

import com.cxuy.framework.util.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class HttpHandlerScanner {
    private static final String TAG = HttpHandlerScanner.class.getName();
    public static Set<Class<?>> scanClass() {
        Class<?> httpHandlerAnnotation = HttpHandler.class;
        Set<Class<?>> result = new HashSet<>();
        try {
            // 获取当前线程的类加载器
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            // 获取类路径下的所有资源
            Enumeration<URL> resources = classLoader.getResources("");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    // 处理文件系统中的类
                    scanDirectory(result, httpHandlerAnnotation, new File(URLDecoder.decode(
                            resource.getFile(), StandardCharsets.UTF_8)), "");
                } else if ("jar".equals(protocol)) {
                    // 处理JAR文件中的类
                    String jarPath = resource.getFile().substring(5, resource.getFile().indexOf("!"));
                    scanJarFile(result, httpHandlerAnnotation, new JarFile(URLDecoder.decode(
                            jarPath, StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.e(TAG, "failure: scan failed, ", e);
        }
        return result;
    }

    /**
     * 扫描目录中的类文件
     */
    private static void scanDirectory(Set<Class<?>> result, Class<?> annotationClass, File directory, String packageName) throws ClassNotFoundException {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                String newPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanDirectory(result, annotationClass, file, newPackage);
            } else if (file.getName().endsWith(".class")) {
                // 处理class文件
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent((Class<? extends Annotation>) annotationClass)) {
                    result.add(clazz);
                }
            }
        }
    }

    /**
     * 扫描JAR文件中的类
     */
    private static void scanJarFile(Set<Class<?>> result, Class<?> annotationClass,
                                    JarFile jarFile) throws ClassNotFoundException, IOException {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.endsWith(".class") && !entry.isDirectory()) {
                // 转换为类名
                String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent((Class<? extends Annotation>) annotationClass)) {
                    result.add(clazz);
                }
            }
        }
        jarFile.close();
    }
}
