package com.cxuy.server.model;

import com.cxuy.framework.constant.Constant;
import com.cxuy.framework.util.JsonUtil;
import com.cxuy.framework.util.TimeUtil;
import com.cxuy.server.BusinessCode;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public class JsonRpc<T> {
    public String timestamp;
    public String message;
    public int code;
    public T data;

    public JsonRpc(String timestamp, String message, BusinessCode code, T data) {
        this(timestamp, message, code.rawValue, data);
    }

    public JsonRpc(String timestamp, String message, int code, T data) {
        this.timestamp = timestamp == null ? TimeUtil.format(TimeUtil.now(), TimeUtil.FORMAT_DATETIME_SECOND) : timestamp;
        this.message = message == null ? Constant.EMPTY_STR : message;
        this.code = code;
        this.data = data;
    }

    public static JsonRpc<Object> empty() {
        return new Builder<>().build();
    }

    public static JsonRpc<Object> failure() {
        return new Builder<>()
                .code(BusinessCode.FAILURE)
                .build();
    }

    public static JsonRpc<Object> failure(BusinessCode code) {
        return new Builder<>()
                .code(code)
                .build();
    }

    public static <M> JsonRpc<M> successful(String message, M data) {
        return new Builder<M>()
                .code(BusinessCode.SUCCESSFUL)
                .message(message)
                .data(data)
                .build();
    }

    public static <M> JsonRpc<M> successful(M data) {
        return new Builder<M>()
                .code(BusinessCode.SUCCESSFUL)
                .data(data)
                .build();
    }

    public String toJson() {
        return JsonUtil.toJson(this);
    }

    public static <M> JsonRpc<M> fromJson(String json) {
        Type type = new TypeToken<JsonRpc<M>>(){}.getType();
        return JsonUtil.fromJson(json, type);
    }

    public static class Builder<D> {
        private String mTimestamp;
        private String msg;
        public int mCode;
        public D mData;

        public Builder() {
            mTimestamp = TimeUtil.format(TimeUtil.now(), TimeUtil.FORMAT_DATETIME_SECOND);
            msg = Constant.EMPTY_STR;
            mCode = BusinessCode.SUCCESSFUL.rawValue;
            mData = null;
        }

        public Builder<D> timestamp(String dateTime) {
            mTimestamp = dateTime;
            return this;
        }

        public Builder<D> message(String message) {
            if(message == null) {
                return this;
            }
            msg = message;
            return this;
        }

        public Builder<D> code(BusinessCode code) {
            this.mCode = code.rawValue;
            return this;
        }

        public Builder<D> code(int code) {
            this.mCode = code;
            return this;
        }

        public Builder<D> data(D data) {
            mData = data;
            return this;
        }

        public JsonRpc<D> build() {
            return new JsonRpc<>(mTimestamp, msg, mCode, mData);
        }
    }
}
