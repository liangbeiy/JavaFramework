/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.struct.node;

public class DoubleNode<T> implements Node<T> {
    public T data;
    public DoubleNode<T> prev;
    public DoubleNode<T> next;

    public static <M> DoubleNode<M> createHead() {
        DoubleNode<M> head = new DoubleNode<>();
        head.prev = head;
        head.next = head;
        return head;
    }

    public static <M> void remove(DoubleNode<M> node) {
        DoubleNode<M> prev = node.prev;
        DoubleNode<M> next = node.next;
        prev.next = next;
        next.prev = prev;

        node.next = null;
        node.prev = null;
    }

    public static <M> void insertForward(DoubleNode<M> insertNode, DoubleNode<M> targetNode) {
        insertNode.prev = targetNode.prev;
        insertNode.next = targetNode;
        targetNode.prev.next = insertNode;
        targetNode.prev = insertNode;
    }

    public static <M> void insertBack(DoubleNode<M> insertNode, DoubleNode<M> targetNode) {
        insertNode.prev = targetNode;
        insertNode.next = targetNode.next;
        targetNode.next.prev = insertNode;
        targetNode.next = insertNode;
    }

    public DoubleNode() {
        this(null, null, null);
    }

    public DoubleNode(T data) {
        this(data, null, null);
    }

    public DoubleNode(T data, DoubleNode<T> p, DoubleNode<T> n) {
        this.data = data;
        prev = p;
        next = n;
    }
}
