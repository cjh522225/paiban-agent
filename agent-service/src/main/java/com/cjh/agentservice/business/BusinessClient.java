package com.cjh.agentservice.business;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface BusinessClient {

    JsonNode get(String token, String path, Map<String, String> params);

    JsonNode post(String token, String path, Object body);

    JsonNode put(String token, String path, Object body);

    JsonNode delete(String token, String path);

    JsonNode delete(String token, String path, Object body);
}
