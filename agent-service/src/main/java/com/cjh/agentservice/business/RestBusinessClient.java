package com.cjh.agentservice.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RestBusinessClient implements BusinessClient {

    private static final int OK_CODE = 200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestBusinessClient(String baseUrl, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode get(String token, String path, Map<String, String> params) {
        return restClient.get()
                .uri(builder -> {
                    builder.path(path);
                    if (params != null) {
                        params.forEach((k, v) -> {
                            if (v != null && !v.isBlank()) {
                                builder.queryParam(k, v);
                            }
                        });
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> parse(path, response.getStatusCode().value(), readBody(response.getBody())));
    }

    @Override
    public JsonNode post(String token, String path, Object body) {
        return exchange(HttpMethod.POST, token, path, body);
    }

    @Override
    public JsonNode put(String token, String path, Object body) {
        return exchange(HttpMethod.PUT, token, path, body);
    }

    @Override
    public JsonNode delete(String token, String path) {
        return exchange(HttpMethod.DELETE, token, path, null);
    }

    @Override
    public JsonNode delete(String token, String path, Object body) {
        return exchange(HttpMethod.DELETE, token, path, body);
    }

    private JsonNode exchange(HttpMethod method, String token, String path, Object body) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.exchange((request, response) -> parse(path, response.getStatusCode().value(), readBody(response.getBody())));
    }

    private String readBody(InputStream stream) {
        try (stream) {
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessApiException(500, "读取业务系统响应失败：" + e.getMessage());
        }
    }

    private JsonNode parse(String path, int httpStatus, String body) {
        if (httpStatus == 401) {
            throw new BusinessApiException(401, "未登录或登录已过期，请重新登录后再试");
        }
        if (httpStatus == 403) {
            throw new BusinessApiException(403, "当前账号无操作权限");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != OK_CODE) {
                String message = root.path("message").asText(root.path("msg").asText("业务系统返回异常"));
                throw new BusinessApiException(code, message);
            }
            JsonNode data = root.get("data");
            if (data == null) {
                JsonNode rows = root.get("rows");
                if (rows != null) {
                    return root;
                }
                return objectMapper.nullNode();
            }
            return data;
        } catch (BusinessApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessApiException(httpStatus, "业务系统 " + path + " 响应解析失败：" + e.getMessage());
        }
    }
}
