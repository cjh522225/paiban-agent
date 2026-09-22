package com.Firefire.paiban.interceptor;

import com.Firefire.paiban.common.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    private static final Set<String> ADMIN_PATH_PREFIXES = Set.of(
        "/api/users", "/api/dormitories", "/api/offices",
        "/api/time-slots", "/api/holidays", "/api/semester",
        "/api/schedules", "/api/messages", "/api/statistics",
        "/api/multi-duty", "/api/discipline", "/api/duty-adjustments"
    );

    // GET 也要求管理员的前缀（敏感数据视图：用户列表/统计/纪律）
    private static final Set<String> STRICT_ADMIN_PREFIXES = Set.of(
        "/api/users", "/api/statistics", "/api/discipline"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendUnauthorized(response, "未登录或token已过期");
            return false;
        }

        try {
            token = token.substring(7);
            if (jwtUtils.isTokenExpired(token)) {
                sendUnauthorized(response, "token已过期");
                return false;
            }

            String role = jwtUtils.getRole(token);
            String path = request.getRequestURI();
            String method = request.getMethod();

            request.setAttribute("userId", jwtUtils.getUserId(token));
            request.setAttribute("username", jwtUtils.getUsername(token));
            request.setAttribute("role", role);

            if (!"admin".equals(role)) {
                if (!"GET".equalsIgnoreCase(method) && isAdminPath(path, method)) {
                    sendForbidden(response, "无操作权限");
                    return false;
                }
                // 敏感视图 GET 也仅管理员
                if (isStrictAdminPath(path)) {
                    sendForbidden(response, "无操作权限");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            sendUnauthorized(response, "token无效");
            return false;
        }
    }

    private boolean isAdminPath(String path, String method) {        // 用户端消息/附件操作接口（上传、标记已查看/已读/全部已读）放行普通用户
        if (path.startsWith("/api/messages")) {
            if (path.contains("/mark-viewed") || path.contains("/read-all") || path.contains("/upload")
                    || path.matches(".*/messages/\\d+/read$")
                    || ("DELETE".equalsIgnoreCase(method) && path.matches(".*/messages/\\d+$"))) {
                return false;
            }
            return true;
        }
        // 请假审批/编辑仅管理员可操作（普通用户仅查看、提交、撤销自己的申请）
        if (path.startsWith("/api/leaves")) {
            if (path.endsWith("/approve") || ("/api/leaves".equals(path) && "PUT".equalsIgnoreCase(method))) {
                return true;
            }
            return false;
        }
        // 换班请求：学生提交申请、查看自己的、撤销自己的申请放行；列表/同意/拒绝仅管理员
        // 撤销时控制器会校验必须是本人自己的申请
        if (path.startsWith("/api/swap-requests")) {
            if (("POST".equalsIgnoreCase(method) && "/api/swap-requests".equals(path))
                    || path.endsWith("/my")
                    || ("DELETE".equalsIgnoreCase(method) && path.matches("/api/swap-requests/\\d+"))) {
                return false;
            }
            return true;
        }
        // 多排申请：学生提交申请、查看自己的、撤销自己的申请放行；列表/审批仅管理员
        if (path.startsWith("/api/multi-duty")) {
            if (("POST".equalsIgnoreCase(method) && "/api/multi-duty".equals(path))
                    || path.endsWith("/my")
                    || ("DELETE".equalsIgnoreCase(method) && path.matches("/api/multi-duty/\\d+"))) {
                return false;
            }
            return true;
        }
        for (String prefix : ADMIN_PATH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isStrictAdminPath(String path) {
        // 学生端查看自己的换班申请/多排申请放行
        if (path.equals("/api/swap-requests/my") || path.equals("/api/multi-duty/my")) return false;
        for (String prefix : STRICT_ADMIN_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + msg + "\"}");
    }

    private void sendForbidden(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + msg + "\"}");
    }
}
