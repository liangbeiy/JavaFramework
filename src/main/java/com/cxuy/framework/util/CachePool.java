package com.cxuy.framework.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class CachePool<K, M> {
    @FunctionalInterface
    public static interface CachePoolCheckCallback<K, M> {
        void callback(CachePool<K, M> pool, K key, M model); 
    }

    @FunctionalInterface
    public static interface ItemHasDeleted<K, M> {
        void hasRemoved(CachePool<K, M> pool, K key, M model); 
    }

    private static final String DISPATCHER_NAME = "com.util.CachePool#worker"; 

    private final Object nodePoolLock = new Object(); 
    private final Queue<Node<K, M>> nodePool = new LinkedList<>(); 

    private final DispatcherQueue worker = new DispatcherQueue(DISPATCHER_NAME); 

    private final Map<K, Node<K, M>> searchMap = new HashMap<>(); 
    private final Node<K, M> head = new Node<>(); 

    private final Object listenersLock = new Object(); 
    private final Set<ItemHasDeleted<K, M>> listeners = new HashSet<>(); 

    public CachePool() {
        head.prevNode = head; 
        head.nextNode = head; 
    }

    public void put(K key, M model) {
        if(key == null) {
            return; 
        }
        if(searchMap.containsKey(key)) {
            worker.async(() -> {
                Node<K, M> node = searchMap.remove(key); 
                node.model = model; 
                remove(node);
                insert(head, node);
            });
            return; 
        }
        Node<K, M> node = obtain(); 
        node.key = key; 
        node.model = model; 
        worker.async(() -> {
            searchMap.put(key, node); 
            insert(head, node);
        });
    }

    public void remove(K key) {
        remove(key, null);
    }

    public void remove(K key, ItemHasDeleted<K, M> listener) {
        if(key == null) {
            return; 
        }
        worker.async(() -> {
            Node<K, M> node = searchMap.remove(key); 
            if(node == null) {
                return; 
            }
            remove(node);
            synchronized(listenersLock) {
                if(listener == null || !listeners.contains(listener)) {
                    listener.hasRemoved(this, key, node.model);
                }
                for(ItemHasDeleted<K, M> l : listeners) {
                    l.hasRemoved(this, key, node.model);
                }
            }
            recycle(node);
        });
    }

    public void get(K key, CachePoolCheckCallback<K, M> callback) {
        if(key == null) {
            return; 
        }
        worker.async(() -> {
            Node<K, M> node = searchMap.get(key); 
            if(node == null) {
                callback.callback(this, key, null);
                return; 
            }
            remove(node);
            insert(head, node);
            callback.callback(this, key, node.model);
        });
    }

    public void addRemoveListener(ItemHasDeleted<K, M> listener) {
        if(listener == null) {
            return; 
        }
        synchronized(listenersLock) {
            if(listeners.contains(listener)) {
                return; 
            }
            listeners.add(listener); 
        }
    }

    public void removeDeleteListener(ItemHasDeleted<K, M> listener) {
        if(listener == null) {
            return; 
        }
        synchronized(listenersLock) {
            listeners.remove(listener); 
        }
    }
    
    private Node<K, M> obtain() {
        synchronized(nodePoolLock) {
            if(nodePool.isEmpty()) {
                return new Node<>(); 
            }
            return nodePool.poll(); 
        }
    }

    private void recycle(Node<K, M> node) {
        if(node == null) {
            return; 
        }
        synchronized(nodePoolLock) {
            nodePool.offer(node); 
        }
    }

    private void remove(Node<K, M> node) {
        Node<K, M> prev = node.prevNode; 
        Node<K, M> next = node.nextNode; 
        prev.nextNode = next; 
        next.prevNode = prev; 
        node.nextNode = null; 
        node.prevNode = null; 
    }

    private void insert(Node<K, M> prev, Node<K, M> node){
        node.prevNode = prev; 
        node.nextNode = prev.nextNode; 
        Node<K, M> nextNode = prev.nextNode; 
        prev.nextNode = node; 
        nextNode.prevNode = node; 
    }

    private static class Node<K, M> {
        private K key; 
        private M model; 
        private Node<K, M> prevNode; 
        private Node<K, M> nextNode; 
    }
}
