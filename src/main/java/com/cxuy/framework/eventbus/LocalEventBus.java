/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.eventbus;

import com.cxuy.framework.coroutine.DispatchQueue;
import com.cxuy.framework.eventbus.annotate.Subscribe;
import com.cxuy.framework.util.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LocalEventBus implements EventBus {
    private static final String TAG = "EventBus";

    private static final String WORKER_NAME = "com.util.EventBus#Worker";

    private final Map<Class<?>, Set<SubscriberWrapper>> subscribers = new HashMap<>();
    private final Map<Object, SubscriberWrapper> registers = new HashMap<>();

    private final DispatchQueue worker = new DispatchQueue(WORKER_NAME);

    private static final LocalEventBus INSTANCE = new LocalEventBus();

    public static EventBus get() {
        return INSTANCE;
    }

    @Override
    public void register(Object subscriber) {
        if(subscriber == null) {
            return;
        }
        worker.async((context) -> {
            if(registers.containsKey(subscriber)) {
                return;
            }
            // 生成包装实例
            SubscriberWrapper wrapper = generateWrapper(subscriber);
            registers.put(subscriber, wrapper);
            //记录这个event有哪些响应者
            for(Class<?> event : wrapper.getEvents()) {
                Set<SubscriberWrapper> subSet = subscribers.get(event);
                if(subSet == null) {
                    subSet = new HashSet<>();
                    subscribers.put(event, subSet);
                }
                subSet.add(wrapper);
            }
        });
    }

    @Override
    public void post(Object event) {
        if(event == null) {
            return;
        }
        worker.async((context) -> {
            Class<?> eventType = event.getClass();
            Set<SubscriberWrapper> wrappers = subscribers.get(eventType);
            for(SubscriberWrapper wrapper : wrappers) {
                wrapper.handleEvent(eventType, event);
            }
        });
    }

    @Override
    public void unregister(Object subscriber) {
        if(subscriber == null) {
            return;
        }
        worker.async((context) -> {
            SubscriberWrapper wrapper = registers.get(subscriber);
            if(wrapper == null) {
                return;
            }

            // 从快捷注册中删除掉
            Set<Class<?>> allEventsClass = wrapper.getEvents();
            for(Class<?> paramClass : allEventsClass) {
                Set<SubscriberWrapper> wrapperSet = subscribers.get(paramClass);
                if(wrapperSet != null) {
                    wrapperSet.remove(wrapper);
                    // 当前事件为空，删除
                    if(wrapperSet.isEmpty()) {
                        subscribers.remove(paramClass);
                    }
                }
            }

            // 从注册者内删除
            registers.remove(subscriber);
        });
    }

    private SubscriberWrapper generateWrapper(Object subscriber) {
        // 获取订阅者的类名与全部注册响应的方法
        Class<?> clazz = subscriber.getClass();
        Set<Method> annotatedMethods = new HashSet<>();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                annotatedMethods.add(method);
            }
        }
        SubscriberWrapper wrapper = new SubscriberWrapper(subscriber);
        // 对全部方法按照事件类型生成map
        for(Method method : annotatedMethods) {
            Class<?> paramClass = method.getParameterTypes()[0];
            Set<Method> wrapperMethod = wrapper.methods.get(paramClass);
            if(wrapperMethod == null) {
                wrapperMethod = new HashSet<>();
                wrapper.methods.put(paramClass, wrapperMethod);
            }
            wrapperMethod.add(method);
        }
        return wrapper;
    }

    private static class SubscriberWrapper {
        private final Object subscriber;

        private final Map<Class<?>, Set<Method>> methods;
        public SubscriberWrapper(Object s) {
            subscriber = s;
            methods = new HashMap<>();
        }

        public void handleEvent(Class<?> paramClass, Object event) {
            Set<Method> handleMethods = this.methods.get(paramClass);
            if(handleMethods == null) {
                return;
            }
            for(Method method : handleMethods) {
                try {
                    method.invoke(subscriber, event);
                } catch (IllegalAccessException e) {
                    Logger.e(TAG, "EventBus: arguments is error", e);
                } catch (InvocationTargetException e) {
                    Logger.e(TAG, "EventBus: arguments is error, InvocationTargetException", e);
                } catch(Exception e) {
                    Logger.e(TAG, "EventBus: exception occurred. ", e);
                }
            }
        }

        public Set<Class<?>> getEvents() {
            return methods.keySet();
        }
    }
}
