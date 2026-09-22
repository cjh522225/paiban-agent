package com.cjh.agentservice.mcp;

import com.cjh.agentservice.audit.AuditTrail;
import com.cjh.agentservice.business.BusinessClient;
import com.cjh.agentservice.business.TokenResolver;
import com.cjh.agentservice.config.AgentProperties;
import com.cjh.agentservice.rag.RagService;
import com.cjh.agentservice.rag.RagTools;
import com.cjh.agentservice.tools.dorm.DormTools;
import com.cjh.agentservice.tools.paiban.PaibanTools;
import com.cjh.agentservice.workflow.DormAssistantService;
import com.cjh.agentservice.workflow.DormAssistantTools;
import com.cjh.agentservice.workflow.HolidayConfigService;
import com.cjh.agentservice.workflow.HolidayPreviewTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.function.Function;

/**
 * 把业务工具暴露给 MCP Server（SSE），供 DeepSeekBall 等 MCP 客户端调用。
 * 按模式装配：paiban 模式暴露排班工具 + 制度检索 + 节假日预览；dorm 模式暴露宿舍工具 + 制度检索。
 * 只暴露只读工具；写操作（applyHolidayConfig）不对外暴露。
 */
@Component
public class McpToolProvider implements ToolCallbackProvider {

    private final BusinessClient businessClient;
    private final TokenResolver tokenResolver;
    private final ObjectMapper objectMapper;
    private final AuditTrail auditTrail;
    private final RagTools ragTools;
    private final HolidayPreviewTools previewTools;
    private final DormAssistantTools dormAssistantTools;
    private final boolean dormMode;
    private final boolean demoMode;

    public McpToolProvider(BusinessClient businessClient, TokenResolver tokenResolver,
                           ObjectMapper objectMapper, AuditTrail auditTrail, RagService ragService,
                           HolidayConfigService holidayConfigService, AgentProperties agentProperties,
                           DormAssistantService dormAssistantService) {
        this.businessClient = businessClient;
        this.tokenResolver = tokenResolver;
        this.objectMapper = objectMapper;
        this.auditTrail = auditTrail;
        this.ragTools = new RagTools(ragService, objectMapper);
        this.previewTools = new HolidayPreviewTools(holidayConfigService, objectMapper);
        this.dormAssistantTools = new DormAssistantTools(dormAssistantService, objectMapper);
        this.dormMode = agentProperties.isDormMode();
        this.demoMode = agentProperties.isDemoMode();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        Function<String, Object[]> factory = token -> dormMode
                ? new Object[]{new DormTools(businessClient, token, objectMapper), ragTools, dormAssistantTools}
                : new Object[]{new PaibanTools(businessClient, token, objectMapper), ragTools, previewTools};
        java.util.function.Supplier<String> tokenSupplier = demoMode
                ? () -> "demo-token"
                : () -> tokenResolver.resolve(null);
        ToolCallback[] template = ToolCallbacks.from(factory.apply("mcp-template"));
        return Arrays.stream(template)
                .map(callback -> (ToolCallback) new DynamicTokenToolCallback(
                        callback, factory, tokenSupplier, auditTrail))
                .toArray(ToolCallback[]::new);
    }
}
