package com.cxuy.framework.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cxuy.framework.eventbus.annotate.Subscribe;
import com.cxuy.framework.util.DispatcherQueue;
import com.cxuy.framework.util.Logger;

public class EventBus {
    private static final String TAG = "EventBus"; 

    public static final EventBus standard = new EventBus(); 

    private static final String WOKER_NAME = "com.util.EventBus#Worker"; 

    private final Map<Class<?>, Set<SubscriberWrapper>> subscribers = new HashMap<>(); 
    private final Map<Object, SubscriberWrapper> registers = new HashMap<>(); 

    private DispatcherQueue worker = new DispatcherQueue(WOKER_NAME); 

    public void register(Object subscriber) {
        if(subscriber == null) {
            return; 
        }
        worker.async(() -> {
            if(registers.containsKey(subscriber)) {
                return; 
            }
            SubscriberWrapper wrapper = generateWrapper(subscriber); 
            registers.put(subscriber, wrapper); 

            Map<Class<?>, Set<Method>> subscriberMethods = wrapper.methods; 
            for(Map.Entry<Class<?>, Set<Method>> entry : subscriberMethods.entrySet()) {
                Class<?> paramClass = entry.getKey(); 
                Set<SubscriberWrapper> subSet = subscribers.get(paramClass); 
                if(subSet == null) {
                    subSet = new HashSet<>(); 
                    subscribers.put(paramClass, subSet); 
                }
                subSet.add(wrapper); 
            }
        });
    }

    public void post(Object event) {
        if(event == null) {
            return; 
        }
        worker.async(() -> {
            Class<?> eventType = event.getClass(); 
            Set<SubscriberWrapper> wrappers = subscribers.get(eventType); 
            for(SubscriberWrapper wrapper : wrappers) {
                wrapper.post(eventType, event);
            }
        });
    }

    public void unregister(Object subscriber) {
        if(subscriber == null) {
            return; 
        }
        worker.async(() -> {
            SubscriberWrapper wrapper = registers.get(subscriber); 
            if(wrapper == null) {
                return; 
            }

            Set<Class<?>> allEventsClass = wrapper.getEvents(); 
            for(Class<?> paramClass : allEventsClass) {
                Set<SubscriberWrapper> wrapperSet = subscribers.get(paramClass); 
                if(wrapperSet != null) {
                    wrapperSet.remove(wrapper); 
                    if(wrapperSet.isEmpty()) {
                        subscribers.remove(paramClass); 
                    }
                }
            }
        }); 
    }

    private final SubscriberWrapper generateWrapper(Object subscriber) {
        Class<?> clazz = subscriber.getClass(); 
        Set<Method> annotatedMethods = new HashSet<>();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                annotatedMethods.add(method);
            }
        }
        SubscriberWrapper wrapper = new SubscriberWrapper(subscriber); 
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

        public void post(Class<?> paramClass, Object event) {
            Set<Method> postMethods = this.methods.get(paramClass); 
            if(postMethods == null) {
                return; 
            }
            for(Method method : postMethods) {
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
