package com.cjh.agentservice.mcp;

import com.cjh.agentservice.audit.AuditTrail;
import com.cjh.agentservice.audit.AuditedToolCallback;
import com.cjh.agentservice.business.BusinessApiException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MCP 场景下的工具回调：调用时才解析 token（服务账号），并按 token 缓存整套工具实例。
 * 这样 MCP 客户端无需携带 JWT，也能以服务账号身份执行只读查询。
 */
public class DynamicTokenToolCallback implements ToolCallback {

    private final ToolCallback template;
    private final Function<String, Object[]> toolFactory;
    private final java.util.function.Supplier<String> tokenSupplier;
    private final AuditTrail auditTrail;

    private final Map<String, Map<String, ToolCallback>> cache = new ConcurrentHashMap<>();

    public DynamicTokenToolCallback(ToolCallback template, Function<String, Object[]> toolFactory,
                                    java.util.function.Supplier<String> tokenSupplier, AuditTrail auditTrail) {
        this.template = template;
        this.toolFactory = toolFactory;
        this.tokenSupplier = tokenSupplier;
        this.auditTrail = auditTrail;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return template.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return template.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate().call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate().call(toolInput, toolContext);
    }

    private ToolCallback delegate() {
        String toolName = template.getToolDefinition().name();
        String token = tokenSupplier.get();
        if (token == null) {
            throw new BusinessApiException(401, "MCP 服务账号不可用，无法执行工具 " + toolName);
        }
        ToolCallback callback = cache.computeIfAbsent(token, key -> buildCallbacks(key)).get(toolName);
        if (callback == null) {
            throw new BusinessApiException(500, "MCP 工具未注册：" + toolName);
        }
        return callback;
    }

    private Map<String, ToolCallback> buildCallbacks(String token) {
        ToolCallback[] callbacks = org.springframework.ai.support.ToolCallbacks.from(toolFactory.apply(token));
        Map<String, ToolCallback> map = new ConcurrentHashMap<>();
        Arrays.stream(callbacks).forEach(callback ->
                map.put(callback.getToolDefinition().name(), new AuditedToolCallback(callback, auditTrail)));
        return map;
    }
}
