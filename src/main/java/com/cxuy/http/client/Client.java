/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.http.client;

import com.cxuy.framework.annotation.NonNull;
import com.cxuy.framework.coroutine.DispatcherQueue;
import com.cxuy.framework.util.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Client {
    public interface ResponseCallback {
        void response(@NonNull Client client, @NonNull Request request, Response response);
    }

    public static final int DEFAULT_TIMEOUT_CONNECT = 5000;
    public static final int DEFAULT_TIMEOUT_RESPONSE = 10000;

    private static final String TAG = "Client";

    public static Client getInstance() {
        return HOLDER.INSTANCE;
    }

    private int connectTimeout;
    private int responseTimeout;

    private final Object interceptorsLock = new Object();
    private final Set<Interceptor> interceptors = new HashSet<>();

    public Client() {
        this(DEFAULT_TIMEOUT_CONNECT, DEFAULT_TIMEOUT_RESPONSE);
    }

    public Client(int timeout) {
        this(timeout, timeout);
    }

    public Client(int connectTimeout, int responseTimeout) {
        this.connectTimeout = connectTimeout;
        this.responseTimeout = responseTimeout;
    }

    public void addInterceptor(Interceptor interceptor) {
        if(interceptor == null) {
            return;
        }
        synchronized(interceptorsLock) {
            interceptors.add(interceptor);
        }
    }

    public void removeInterceptor(Interceptor interceptor) {
        if(interceptor == null) {
            return;
        }
        synchronized(interceptorsLock) {
            interceptors.remove(interceptor);
        }
    }

    public void async(@NonNull Request request, ResponseCallback callback) {
        if(request == null) {
            return;
        }
        synchronized(interceptorsLock) {
            for(Interceptor interceptor : interceptors) {
                interceptor.beforeSubmitRequest(this, request);
            }
        }
        DispatcherQueue.io.async(() -> {
            Response response = sync(request);
            callback.response(this, request, response);
        });
        synchronized(interceptorsLock) {
            for(Interceptor interceptor : interceptors) {
                interceptor.submitRequest(this, request, true);
            }
        }
    }

    private Response sync(@NonNull Request request) {
        if(request == null) {
            return null;
        }
        synchronized(interceptorsLock) {
            for(Interceptor interceptor : interceptors) {
                interceptor.beforeSubmitRequest(this, request);
            }
            for(Interceptor interceptor : interceptors) {
                interceptor.submitRequest(this, request, true);
            }
        }
        return handleRequest(request);
    }

    private Response handleRequest(@NonNull Request request) {
        if(request == null) {
            return null;
        }
        synchronized(interceptorsLock) {
            for(Interceptor interceptor : interceptors) {
                interceptor.beforeHandleRequest(this, request);
            }
        }
        // handle
        Executor executor = new Executor(request, connectTimeout, responseTimeout);
        Response response = null;
        try {
            response = executor.execute();
        } catch (IOException e) {
            Logger.e(TAG, "An exception occurred while requesting HTTP", e);
        }
        synchronized(interceptorsLock) {
            for(Interceptor interceptor : interceptors) {
                interceptor.handleRequest(this, request, response);
            }
        }
        return response;
    }

    private static class HOLDER {
        private static final Client INSTANCE = new Client();
    }
}
