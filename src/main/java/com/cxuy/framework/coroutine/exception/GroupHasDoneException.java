/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.coroutine.exception;

public class GroupHasDoneException extends GroupStatusErrorException {
    public GroupHasDoneException() {
        super("DispatchGroup has been marked DONE, you need to create a new group instead of using the old instance. ");
    }
}
