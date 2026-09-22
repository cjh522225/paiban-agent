package com.cjh.agentservice.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解析请求 JWT：优先使用调用方传入的 Authorization，缺省时回退服务账号。
 * 服务账号不可用（如 RuoYi 开启验证码）时返回 null，由工具层给出"请先登录"的友好提示，避免整请求 500。
 */
public class TokenResolver {

    private static final Logger log = LoggerFactory.getLogger(TokenResolver.class);

    private final ServiceAccountAuth serviceAccountAuth;

    public TokenResolver(ServiceAccountAuth serviceAccountAuth) {
        this.serviceAccountAuth = serviceAccountAuth;
    }

    public String resolve(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        if (serviceAccountAuth == null) {
            return null;
        }
        try {
            return serviceAccountAuth.token();
        } catch (BusinessApiException e) {
            log.warn("服务账号不可用，将以未登录态执行工具：{}", e.getMessage());
            return null;
        }
    }
}
