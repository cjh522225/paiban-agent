package com.cjh.agentservice.audit;

import com.cjh.agentservice.business.BusinessApiException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Supplier;

/**
 * 工具调用审计装饰器：记录耗时与结果；业务异常转为结构化错误交给模型解释，避免整轮对话失败。
 */
public class AuditedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final AuditTrail auditTrail;

    public AuditedToolCallback(ToolCallback delegate, AuditTrail auditTrail) {
        this.delegate = delegate;
        this.auditTrail = auditTrail;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return execute(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String execute(String toolInput, Supplier<String> action) {
        String toolName = delegate.getToolDefinition().name();
        long start = System.nanoTime();
        try {
            String result = action.get();
            auditTrail.record(toolName, toolInput, elapsedMs(start), true, null);
            return result;
        } catch (Exception e) {
            BusinessApiException business = extractBusinessError(e);
            String message = business != null ? business.getMessage() : e.getMessage();
            auditTrail.record(toolName, toolInput, elapsedMs(start), false, message);
            if (business != null) {
                return "{\"error\":\"" + escape(message) + "\"}";
            }
            throw e;
        }
    }

    private BusinessApiException extractBusinessError(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 5; depth += 1) {
            if (current instanceof BusinessApiException business) {
                return business;
            }
            current = current.getCause();
        }
        return null;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String escape(String text) {
        if (text == null) {
            return "未知错误";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
