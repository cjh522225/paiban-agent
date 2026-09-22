package com.cjh.agentservice.business;

import com.cjh.agentservice.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务账号登录（无前端 JWT 时兜底：脚本 / MCP / 定时任务场景）。
 * 不同业务系统的登录路径与 token 位置不同，通过 LoginSpec 适配：
 * - paiban: POST /api/auth/login {username,password} → data.token
 * - dorm(RuoYi): POST /login {username,password,code,uuid} → 根字段 token
 */
public class ServiceAccountAuth {

    private static final Logger log = LoggerFactory.getLogger(ServiceAccountAuth.class);
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(12);

    public record LoginSpec(String path, Map<String, String> extraBody, boolean tokenAtRoot) {

        public static LoginSpec paiban() {
            return new LoginSpec("/api/auth/login", Map.of(), false);
        }

        public static LoginSpec dorm() {
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("code", "");
            extra.put("uuid", "");
            return new LoginSpec("/login", extra, true);
        }
    }

    private final RestClient restClient;
    private final String username;
    private final String password;
    private final LoginSpec loginSpec;

    private volatile String cachedToken;
    private volatile Instant cachedAt = Instant.EPOCH;

    public ServiceAccountAuth(String baseUrl, String username, String password, LoginSpec loginSpec) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.username = username;
        this.password = password;
        this.loginSpec = loginSpec;
    }

    public static ServiceAccountAuth from(String mode, AgentProperties.Business business) {
        LoginSpec spec = mode != null && mode.toLowerCase().startsWith("dorm")
                ? LoginSpec.dorm()
                : LoginSpec.paiban();
        return new ServiceAccountAuth(business.getBaseUrl(), business.getServiceUsername(),
                business.getServicePassword(), spec);
    }

    public String token() {
        if (cachedToken != null && Instant.now().isBefore(cachedAt.plus(REFRESH_INTERVAL))) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(cachedAt.plus(REFRESH_INTERVAL))) {
                return cachedToken;
            }
            cachedToken = login();
            cachedAt = Instant.now();
            return cachedToken;
        }
    }

    private String login() {
        try {
            Map<String, Object> body = new LinkedHashMap<>(loginSpec.extraBody());
            body.put("username", username);
            body.put("password", password);
            var root = restClient.post()
                    .uri(loginSpec.path())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);
            if (root == null) {
                throw new BusinessApiException(500, "服务账号登录失败：空响应");
            }
            String message = root.path("msg").isMissingNode() ? root.path("message").asText("") : root.path("msg").asText("");
            String token = loginSpec.tokenAtRoot()
                    ? root.path("token").asText(null)
                    : root.path("data").path("token").asText(null);
            if (token == null || token.isBlank()) {
                throw new BusinessApiException(500, "服务账号登录失败：" + (message.isBlank() ? "响应中没有 token" : message));
            }
            log.info("服务账号 {} 登录成功（{}）", username, loginSpec.path());
            return token;
        } catch (BusinessApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessApiException(500, "服务账号登录失败：" + e.getMessage());
        }
    }
}
