package com.cxuy.handler;

import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.Logger;
import com.cxuy.server.SimpleHttpServer;
import com.cxuy.server.annotation.HandlerMethod;
import com.cxuy.server.annotation.HttpHandler;
import com.cxuy.server.annotation.RequestBody;

@HttpHandler(path = "/helloworld")
public class WorldHandler extends SimpleHttpServer.Handler {

    @HandlerMethod
    public void post(String name, @RequestBody Body body) {
        Logger.d("WorldHandler", "receive " + name + ", Body=" + JsonUtil.toJson(body));
    }

    public static class Body {
        String name;
        int id;
    }
}
