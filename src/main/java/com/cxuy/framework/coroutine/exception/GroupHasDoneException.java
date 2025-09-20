/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.exception;

public class GroupHasDoneException extends RuntimeException {
    public GroupHasDoneException() {
        super("DispatchGroup has been marked DONE, you need to create a new group instead of use the old instance. ");
    }
}
