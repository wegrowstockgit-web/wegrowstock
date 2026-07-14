package com.invsys.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class JsonMaps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonMaps() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }
}
