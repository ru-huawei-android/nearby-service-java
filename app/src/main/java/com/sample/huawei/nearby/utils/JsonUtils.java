package com.sample.huawei.nearby.utils;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class JsonUtils {
    private static final String TAG = JsonUtils.class.getSimpleName();
    private static final Gson GSON = new Gson();

    public static <T> T json2Object(String jsonStr, @NonNull Class<T> objectClass) {
        T obj = null;
        if (TextUtils.isEmpty(jsonStr)) {
            return obj;
        }

        try {
            obj = GSON.fromJson(jsonStr, objectClass);
        } catch (JsonParseException e) {
            Log.e(TAG, "Parse json to object fail, exception: " + e.getMessage());
        }

        return obj;
    }

    public static <T> String object2Json(@NonNull T object) {
        String jsonStr = null;
        try {
            jsonStr = GSON.toJson(object);
        } catch (JsonParseException e) {
            Log.e(TAG, "Parse object to json fail, exception: " + e.getMessage());
        }

        return jsonStr;
    }
}
