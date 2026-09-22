package com.cjh.agentservice.workflow;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 宿舍系统 AI 辅助工具（只读分析）：
 * 违规文本自动分类、导入失败归因建议。分析结果交人工确认后再由业务系统落库。
 */
public class DormAssistantTools {

    private final DormAssistantService dormAssistantService;
    private final ObjectMapper objectMapper;

    public DormAssistantTools(DormAssistantService dormAssistantService, ObjectMapper objectMapper) {
        this.dormAssistantService = dormAssistantService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "classifyViolation", description = "把违规描述文本自动分类为违规类型（晚归/夜不归宿/使用违规电器/卫生不合格/吸烟/留宿他人/其他）"
            + "并评估严重程度，返回类型、严重程度、依据与处理建议。只做分析，不写库，需人工确认后录入。")
    public String classifyViolation(@ToolParam(description = "违规描述文本，如：晚归 30 分钟，第一次") String text) {
        return json(dormAssistantService.classifyViolation(text));
    }

    @Tool(name = "analyzeImportErrors", description = "对 Excel 批量导入的失败明细做归因分析，输出原因与可执行的修复步骤（如床位冲突、学号重复、字段格式错误）。")
    public String analyzeImportErrors(@ToolParam(description = "导入失败明细文本") String errorText) {
        return json(dormAssistantService.analyzeImportErrors(errorText));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }
}
