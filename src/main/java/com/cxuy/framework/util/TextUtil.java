/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util;

public final class TextUtil {
    public static boolean isEmpty(CharSequence text) {
        return text == null || text.isEmpty();
    }

    public static boolean equals(CharSequence t1, CharSequence t2) {
        if(t1 != null && t2 != null) {
            if(t1 == t2) {
                return true;
            }
            if(t1.length() == t2.length()) {
                for(int i = 0; i < t1.length(); i++) {
                    if(t1.charAt(i) != t2.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        return t1 == null && t2 == null;
    }
}
