/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class JsonUtil {
    private static final String TAG = "JsonUtil";
    private static final Gson INSTANCE = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
            .create();

    public static String toJson(Object object) {
        return INSTANCE.toJson(object);
    }

    public static <T> T fromJson(String json, Type type) {
        try {
            return INSTANCE.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, Class<T> rawType, Type... argumentTypes) {
        Type type = TypeToken.getParameterized(rawType, argumentTypes).getType();
        return fromJson(json, type);
    }

    public static <T> T[] fromJsonToArray(String json, Class<T[]> clazz) {
        return INSTANCE.fromJson(json, clazz);
    }

    public static <T> List<T> fromJsonToList(String jsonList, Class<T> elementClass) {
        List<T> list = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(jsonList).getAsJsonArray();
            for(JsonElement element : array) {
                list.add(INSTANCE.fromJson(element, elementClass));
            }
        } catch(Exception e) {
            Logger.e(TAG, "parse fail" + e);
        }
        return list;
    }

    public static class LocalDateTimeTypeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TimeUtil.FORMAT_ISO);

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }
    }
}
