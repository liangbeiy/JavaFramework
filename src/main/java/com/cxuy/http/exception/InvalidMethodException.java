/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.exception;

public class InvalidMethodException extends RuntimeException {
    public InvalidMethodException(String invalidMethod) {
        super("An exception occurred while handling http method whose name is " + invalidMethod);
    }
}
