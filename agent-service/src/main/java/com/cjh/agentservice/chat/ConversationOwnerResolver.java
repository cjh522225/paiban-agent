package com.cjh.agentservice.chat;

import com.cjh.agentservice.business.JwtPeek;
import org.springframework.stereotype.Component;

/**
 * 会话所有者解析：优先取 JWT 中的用户 ID；无登录态时归入 anonymous。
 */
@Component
public class ConversationOwnerResolver {

    public String resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return "anonymous";
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        JwtPeek.Caller caller = JwtPeek.parse(token);
        if (caller.userId() != null) {
            return "u" + caller.userId();
        }
        return caller.username() == null || caller.username().isBlank()
                ? "anonymous"
                : "n" + caller.username();
    }
}
