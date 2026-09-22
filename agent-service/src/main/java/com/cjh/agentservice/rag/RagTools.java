package com.cjh.agentservice.rag;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 制度知识库工具：RAG 检索，回答带出处。
 */
public class RagTools {

    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public RagTools(RagService ragService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "searchPolicy", description = "检索《值班管理制度》《请假管理办法》《换班与多排申请规则》《纪律与考核办法》等规章制度的原文片段。"
            + "回答制度、流程、规则类问题时必须使用本工具；回答需引用 source 与 section 作为出处。")
    public String searchPolicy(
            @ToolParam(description = "用户的问题或关键词，例如：请假需要提前几天申请") String question,
            @ToolParam(required = false, description = "返回片段数，默认 4") Integer topK) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "RAG/制度文档");
        result.put("query", question);
        result.put("passages", ragService.search(question, topK));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }
}
