package com.cjh.agentservice.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 仅解析 JWT payload 用于识别调用者身份（不做签名校验；权限始终由业务系统按透传的 JWT 强制）。
 */
public final class JwtPeek {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtPeek() {
    }

    public record Caller(Long userId, String username, String role) {
    }

    public static Caller parse(String token) {
        if (token == null) {
            return new Caller(null, null, null);
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return new Caller(null, null, null);
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
            Long userId = node.hasNonNull("userId") ? node.get("userId").asLong() : null;
            String username = node.path("username").asText(null);
            String role = node.path("role").asText(null);
            return new Caller(userId, username, role);
        } catch (Exception e) {
            return new Caller(null, null, null);
        }
    }
}
