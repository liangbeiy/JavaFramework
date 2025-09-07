/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeUtil {
    // 常用时间格式
    public static final String FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String FORMAT_COMMON = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String FORMAT_SIMPLE = "yyyy/MM/dd HH:mm:ss";

    // 用于显示的时间格式
    public static final String FORMAT_DATETIME_SECOND = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm";
    public static final String FORMAT_DATE = "yyyy-MM-dd";

    public static String iso() {
        return format(now(), FORMAT_ISO); 
    }

    public static String common() {
        return format(now(), FORMAT_COMMON); 
    }

    public static String dateTime() {
        return format(now(), FORMAT_DATETIME); 
    }

    public static String format(String format) {
        return format(now(), format); 
    }

    public static String format(long millis, String format) {
        Instant instant = Instant.ofEpochMilli (millis);
        LocalDateTime localDateTime = LocalDateTime.ofInstant (instant, ZoneId.systemDefault ());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern (format, Locale.getDefault());
        return localDateTime.format (formatter);
    }

    public static long now() {
        return System.currentTimeMillis(); 
    }
    
    private TimeUtil() throws IllegalAccessException {
        throw new IllegalAccessException("util class can't create any instance"); 
    }
}

