package com.acme.sportplatform.integration.support;

import com.jayway.jsonpath.JsonPath;

public final class JsonPathHelper {

    private JsonPathHelper() {
    }

    public static String read(String json, String path) {
        return JsonPath.read(json, path);
    }
}