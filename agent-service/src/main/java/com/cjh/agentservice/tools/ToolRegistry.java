package com.cjh.agentservice.tools;

import com.cjh.agentservice.audit.AuditTrail;
import com.cjh.agentservice.audit.AuditedToolCallback;
import com.cjh.agentservice.business.BusinessClient;
import com.cjh.agentservice.business.TokenResolver;
import com.cjh.agentservice.config.AgentProperties;
import com.cjh.agentservice.rag.RagService;
import com.cjh.agentservice.rag.RagTools;
import com.cjh.agentservice.tools.dorm.DormTools;
import com.cjh.agentservice.tools.paiban.PaibanTools;
import com.cjh.agentservice.tools.paiban.PaibanWriteTools;
import com.cjh.agentservice.workflow.DormAssistantService;
import com.cjh.agentservice.workflow.DormAssistantTools;
import com.cjh.agentservice.workflow.HolidayApplyTools;
import com.cjh.agentservice.workflow.HolidayConfigService;
import com.cjh.agentservice.workflow.HolidayPreviewTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按当前请求组装工具：JWT 绑定进工具实例 → 数据权限由业务系统强制；
 * 所有工具统一包一层审计装饰器。
 */
@Component
public class ToolRegistry {

    private final BusinessClient businessClient;
    private final TokenResolver tokenResolver;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final RagService ragService;
    private final HolidayConfigService holidayConfigService;
    private final AgentProperties agentProperties;
    private final DormAssistantService dormAssistantService;

    public ToolRegistry(BusinessClient businessClient, TokenResolver tokenResolver,
                        AuditTrail auditTrail, ObjectMapper objectMapper, RagService ragService,
                        HolidayConfigService holidayConfigService, AgentProperties agentProperties,
                        DormAssistantService dormAssistantService) {
        this.businessClient = businessClient;
        this.tokenResolver = tokenResolver;
        this.auditTrail = auditTrail;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
        this.holidayConfigService = holidayConfigService;
        this.agentProperties = agentProperties;
        this.dormAssistantService = dormAssistantService;
    }

    public List<ToolCallback> toolsFor(String authorizationHeader, boolean allowWrites) {
        String token = agentProperties.isDemoMode()
                ? "demo-token"
                : tokenResolver.resolve(authorizationHeader);
        Object ragTools = new RagTools(ragService, objectMapper);
        ToolCallback[] callbacks;
        if (agentProperties.isDormMode()) {
            Object dormTools = new DormTools(businessClient, token, objectMapper);
            Object dormAssistant = new DormAssistantTools(dormAssistantService, objectMapper);
            callbacks = ToolCallbacks.from(dormTools, ragTools, dormAssistant);
        } else {
            Object paibanTools = new PaibanTools(businessClient, token, objectMapper);
            Object previewTools = new HolidayPreviewTools(holidayConfigService, objectMapper);
            if (allowWrites) {
                Object writeTools = new PaibanWriteTools(businessClient, token, objectMapper);
                Object applyTools = new HolidayApplyTools(holidayConfigService, objectMapper, authorizationHeader);
                callbacks = ToolCallbacks.from(paibanTools, ragTools, previewTools, writeTools, applyTools);
            } else {
                callbacks = ToolCallbacks.from(paibanTools, ragTools, previewTools);
            }
        }
        List<ToolCallback> result = new ArrayList<>(callbacks.length);
        for (ToolCallback callback : callbacks) {
            result.add(new AuditedToolCallback(callback, auditTrail));
        }
        return result;
    }
}
