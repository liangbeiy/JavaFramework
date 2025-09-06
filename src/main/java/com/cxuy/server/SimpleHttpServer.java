/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.server;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.annotation.Nullable;
import com.cxuy.framework.context.Context;
import com.cxuy.framework.lifecycle.LifecycleObserver;
import com.cxuy.framework.lifecycle.LifecycleOwner;
import com.cxuy.framework.lifecycle.LifecycleState;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;
import com.cxuy.server.annotation.*;
import com.cxuy.server.model.JsonRpc;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleHttpServer implements Server, LifecycleOwner {

    private static final String TAG = "SimpleServer";

    private static final int DEFAULT_PORT = 5867;
    private static final int MAX_RETRY_TIMES = 10;

    private static final String METHOD_GET = "GET";

    private int port = DEFAULT_PORT;
    private HttpServer server;

    private final Object stateLock = new Object();
    private LifecycleState state = LifecycleState.ANY;

    private final Set<LifecycleObserver> observers = Collections.synchronizedSet(new HashSet<>());

    private final Context context;
    public SimpleHttpServer(Context context) {
        this(context, null);
    }

    public SimpleHttpServer(Context context, LifecycleObserver observer) {
        this.context = context.getFrameworkContext();
        if(observer != null) {
            addObserver(observer);
        }
        setState(LifecycleState.INITED);
    }

    public void start() {
        synchronized(stateLock) {
            if(state.rawValue > LifecycleState.DID_START.rawValue) {
                return;
            }
        }
        int nowTimes = 0;
        while(nowTimes < MAX_RETRY_TIMES && server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            } catch (IOException e) {
                nowTimes++;
                port += 1;
            }
        }
        if(server == null) {
            Logger.e(TAG, "SimpleHttpServer create server failed. ");
            return;
        }
        // 注册处理器
        registerHandler();

        server.start();
        setState(LifecycleState.DID_START);
        Logger.d(TAG, "SimpleHttpServer is running at " + port + " port. ");
    }

    public void stop() {
        if(state.rawValue < LifecycleState.DID_START.rawValue) {
            return;
        }
        server.stop(0);
        server = null;
        setState(LifecycleState.DID_STOP);
    }

    private void registerHandler() {
        try {
            Set<Class<?>> domain = HttpHandlerScanner.scanClass(context);
            for (Class<?> clazz : domain) {
                // 验证是否继承自Handler
                if (!Handler.class.isAssignableFrom(clazz)) {
                    String message = String.format("Warning: %s is ignored, because it uses @%s but does not extend %s", clazz.getName(), HttpHandler.class.getName(), Handler.class.getName());
                    Logger.w(TAG, message);
                    continue;
                }

                // 获取注解中的路径
                HttpHandler httpHandler = clazz.getAnnotation(HttpHandler.class);
                String path = httpHandler.path();
                if (path == null || path.isEmpty()) {
                    String message = String.format("Warning: %s is ignored, because there is a null path in its annotation. ", clazz.getName());
                    Logger.w(TAG, message);
                    continue;
                }

                // 创建处理器实例
                final Handler handlerInstance = createHandlerInstance(clazz);
                if (handlerInstance != null) {
                    List<String> handlePath = handlerInstance.getHandlePath();
                    for(String hPath : handlePath) {
                        // 注册处理器
                        server.createContext(hPath, handlerInstance);
                        String message = String.format("A handler has been registered by %s, path=%s -> handlerClass=%s", SimpleHttpServer.class.getName(), hPath, clazz.getName());
                        Logger.d(TAG, message);
                    }
                } else {
                    String message = String.format("Warning: %s is ignored, because this class cannot create instance.  ", clazz.getName());
                    Logger.w(TAG, message);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "An exception occurred while registering handler", e);
        }
    }

    private Handler createHandlerInstance(Class<?> clazz) {
        try {
            // 处理内部类
            if (clazz.isMemberClass()) {
                // 非静态内部类需要外部类实例
                if (!Modifier.isStatic(clazz.getModifiers())) {
                    Class<?> outerClass = clazz.getDeclaringClass();
                    Object outerInstance = outerClass.getDeclaredConstructor().newInstance();
                    return (Handler) clazz.getDeclaredConstructor(outerClass).newInstance(outerInstance);
                }
            }
            // 普通类或静态内部类
            return (Handler) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            Logger.e(TAG, "An exception occurred while creating instance", e);
            return null;
        }
    }

    @Override
    public LifecycleState getState() {
        synchronized(stateLock) {
            return this.state;
        }
    }

    @Override
    public void setState(LifecycleState state) {
        synchronized(stateLock) {
            this.state = state;
        }
        for(LifecycleObserver observer : observers) {
            observer.lifecycleOnChanged(this, state);
        }
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        if(observer == null || observers.contains(observer)) {
            return;
        }
        observers.add(observer);
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        if(observer == null || !observers.contains(observer)) {
            return;
        }
        observers.remove(observer);
    }

    public static abstract class Handler implements com.sun.net.httpserver.HttpHandler {

        private static final String TAG = Handler.class.getName();

        private final String prePath;
        private final Map<String, List<Method>> paths = new HashMap<>();

        public Handler() {
            // 获取注解的路径
            HttpHandler httpHandlerAnnotation = getClass().getAnnotation(HttpHandler.class);
            prePath = httpHandlerAnnotation.path();

            // 遍历当前类及其所有父类（排除BaseHandler自身）
            Class<?> currentClass = this.getClass();
            while (currentClass != null && currentClass != Handler.class) {
                // 获取当前类的所有方法（包括私有方法）
                Method[] methods = currentClass.getDeclaredMethods();
                for (Method method : methods) {
                    // 筛选带@HandlerMethod注解的方法
                    if (method.isAnnotationPresent(HandlerMethod.class)) {
                        method.setAccessible(true);
                        HandlerMethod methodAnnotation = method.getAnnotation(HandlerMethod.class);
                        String subPath = methodAnnotation.subPath();
                        List<Method> entryMethods = paths.computeIfAbsent(subPath, _ -> new ArrayList<>());
                        entryMethods.add(method);
                    }
                }
                // 继续扫描父类
                currentClass = currentClass.getSuperclass();
            }
        }

        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            String fullPath = getRequestPath(exchange);
            Map<String, String> requestParams = getQueryParameters(exchange);
            String body = getRequestBody(exchange);
            boolean containBody = body != null;
            @Nullable
            Method method;
            try {
                String subPath = fullPath.substring(prePath.length());
                method = dispatch(subPath, requestParams, containBody);
            } catch(IllegalArgumentException e) {
                response(exchange, HttpCode.SERVER_ERROR,JsonRpc.failure().toJson());
                return;
            }
            if(method == null) {
                Logger.w(TAG, "No handling method was found in this class with the name " + getClass().getName());
                response(exchange, HttpCode.ERROR, JsonRpc.failure().toJson());
                return;
            }
            Object[] params = convertParam(method, requestParams, body);
            try {
                Object rpc = method.invoke(this, params);
                response(exchange, HttpCode.SUCCESSFUL, JsonRpc.successful("handle successful", rpc).toJson());
                String message = String.format("The request has been handled successfully by %s", getClass().getName());
                Logger.i(TAG, message);
            } catch (Exception e) {
                Logger.e(TAG, "An exception occurred while executing the method", e);
                response(exchange, HttpCode.SERVER_ERROR, JsonRpc.failure().toJson());
            }
        }

        public final List<String> getHandlePath() {
            List<String> handlePath = new ArrayList<>();
            for(Map.Entry<String, List<Method>> entry : paths.entrySet()) {
                String path = prePath + entry.getKey();
                String[] pathWorld = path.split("/");
                StringBuilder builder = new StringBuilder();
                for(String world : pathWorld) {
                    if(!world.isEmpty()) {
                        builder.append("/").append(world);
                    }
                }
                if(builder.isEmpty()) {
                    builder.append("/");
                }
                handlePath.add(builder.toString());
            }
            return handlePath;
        }

        protected String getRequestPath(HttpExchange exchange) {
            return exchange.getRequestURI().getPath();
        }

        /**
         * 发送文本响应
         */
        protected void response(HttpExchange exchange, HttpCode code, String response) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // 处理OPTIONS预检请求
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(HttpCode.SUCCESSFUL.rawValue, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(new byte[0]);
                }
                return;
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.rawValue, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        protected Map<String, String> getQueryParameters(HttpExchange exchange) {
            // 解析查询参数
            String query = exchange.getRequestURI().getQuery();
            return parseQueryParameters(query);
        }

        @Nullable
        protected String getRequestBody(HttpExchange exchange) {
            if(METHOD_GET.equals(exchange.getRequestMethod())) {
                return null;
            }
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                requestBody = null;
            }
            return requestBody;
        }

        @Nullable
        private Method dispatch(String subPath, Map<String, String> requestParams, boolean containBody) {
            List<Method> methods = paths.get(subPath);
            if(methods.isEmpty()) {
                return null;
            }
            // 寻找匹配执行方法
            Method method = null;
            for(Method m : methods) {
                if(isMatchMethod(requestParams, m, containBody)) {
                    method = m;
                    break;
                }
            }
            return method;
        }

        private Map<String, String> parseQueryParameters(String query) {
            Map<String, String> params = new HashMap<>();
            if (TextUtil.isEmpty(query)) {
                return params;
            }
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                switch(keyValue.length) {
                    case 2:
                        params.put(keyValue[0], keyValue[1]);
                        break;
                    case 1:
                        params.put(keyValue[0], "");
                        break;
                    default:
                        // ignored this param
                        break;
                }
            }
            return params;
        }

        private boolean isMatchMethod(@NonNull Map<String, String> params, @NonNull Method method, boolean requestContainBody) {
            Parameter[] methodParams = method.getParameters();
            if(!(methodParams.length == params.size() || (requestContainBody && params.size() + 1 == methodParams.length))) {
                return false;
            }

            for(Parameter methodParam : methodParams) {
                Param paramAnnotation = methodParam.getAnnotation(Param.class);
                if(paramAnnotation != null) {
                    // 当前被param修饰
                    String paramName = modifyParamName(paramAnnotation, methodParam.getName());
                    if(!params.containsKey(paramName)) {
                        return false;
                    }
                }

                RequestBody bodyAnnotation = methodParam.getAnnotation(RequestBody.class);
                if(bodyAnnotation != null) {
                    if(paramAnnotation != null) {
                        String message = String.format("The Parameter's annotation declares incorrectly, @%s and @%s should not co-exist on the same parameter. ",
                                Param.class.getName(), RequestBody.class.getName());
                        Logger.e(TAG, message);
                        throw new IllegalArgumentException(message);
                    }
                    if(!requestContainBody) {
                        return false;
                    }
                    continue;
                }

                if(paramAnnotation != null) {
                    return true;
                }

                // 没标注的按查询参数处理
                String paramName = methodParam.getName();
                if (!params.containsKey(paramName)) {
                    return false;
                }
            }
            return true;
        }

        private String modifyParamName(@NonNull Param annotation, String methodParamName) {
            String annName = annotation.value();
            return annName == null || annName.isEmpty() ? methodParamName : annName;
        }

        private Object[] convertParam(Method method, Map<String, String> requestParams, @Nullable String body) {
            Parameter[] methodParams = method.getParameters();
            Object[] params = new Object[methodParams.length];

            for(int i = 0; i < methodParams.length; i++) {
                Parameter methodParam = methodParams[i];
                Param paramAnnotation = methodParam.getAnnotation(Param.class);
                if(paramAnnotation != null) {
                    // 当前被param修饰
                    String paramName = modifyParamName(paramAnnotation, methodParam.getName());
                    if(requestParams.containsKey(paramName)) {
                        params[i] = toValue(requestParams.get(paramName), methodParam.getType());
                    }
                }

                RequestBody bodyAnnotation = methodParam.getAnnotation(RequestBody.class);
                if(bodyAnnotation != null) {
                    if(body == null) {
                        String message = String.format("The method has matched and has declared a @%s parameter but receives a null request body. ", RequestBody.class.getName());
                        Logger.e(TAG, message);
                        throw new IllegalArgumentException(message);
                    }
                    params[i] = JsonUtil.fromJson(body, methodParam.getType());
                    continue;
                }

                if(paramAnnotation != null) {
                    continue;
                }

                String paramName = methodParam.getName();
                String value = requestParams.get(paramName);
                params[i] = toValue(value, methodParam.getType());
            }
            return params;
        }

        private Object toValue(String value, Class<?> targetType) {
            if (value == null) return null;
            if (targetType == String.class) {
                return value;
            }
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value);
            }
            Object obj = JsonUtil.fromJson(value, targetType);
            return obj == null ? value : obj;
        }
    }
}
