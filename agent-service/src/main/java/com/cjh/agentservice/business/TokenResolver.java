package com.cjh.agentservice.business;

/**
 * 解析请求 JWT：优先使用调用方传入的 Authorization，缺省时回退服务账号。
 */
public class TokenResolver {

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
        return serviceAccountAuth == null ? null : serviceAccountAuth.token();
    }
}
