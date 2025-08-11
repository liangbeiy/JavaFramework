/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.struct;

import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.struct.node.DoubleNode;

import java.util.HashSet;
import java.util.Set;

public class UniqueQueue<T> {

    private final Set<T> dataSet = new HashSet<>();
    private final DoubleNode<T> head = DoubleNode.createHead();

    public boolean offer(T data) {
        if(data == null || dataSet.contains(data)) {
            return false;
        }
        DoubleNode<T> targetNode = head.prev;
        DoubleNode<T> newNode = new DoubleNode<>(data);
        DoubleNode.insertBack(newNode, targetNode);
        dataSet.add(data);
        return true;
    }

    @Nullable
    public T pop() {
        if(dataSet.isEmpty()) {
            return null;
        }
        DoubleNode<T> node = head.next;
        if(node == null || head == node) {
            throw new ArithmeticException("An exception occurred while popping an element, because the dataSet is not empty but the head#next points to itself or null. ");
        }
        return head.next.data;
    }

    public boolean isEmpty() {
        return dataSet.isEmpty();
    }

    public int size() {
        return dataSet.size();
    }
}
