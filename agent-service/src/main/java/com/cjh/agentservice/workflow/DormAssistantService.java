package com.cjh.agentservice.workflow;

import com.cjh.agentservice.business.BusinessApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 宿舍系统 AI 辅助（只读分析，不落库）：
 * - 违规描述 → 违规类型/严重程度（few-shot 结构化输出）
 * - 导入失败明细 → 原因归因 + 可执行修复建议
 */
@Service
public class DormAssistantService {

    private static final String VIOLATION_TYPES = "晚归、夜不归宿、使用违规电器、卫生不合格、吸烟、留宿他人、其他";

    private final ChatClient chatClient;

    public DormAssistantService(@Lazy ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public DormAssistantDrafts.ViolationDraft classifyViolation(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessApiException(400, "请提供违规描述文本");
        }
        try {
            return chatClient.prompt()
                    .system("""
                            你是宿舍违规记录分类助手。把违规描述映射为结构化结果：
                            - violationType：只能从固定字典中选择：%s
                            - severity：低 / 中 / 高
                            - reason：判断依据（一句话）
                            - suggestion：处理建议（一句话）
                            - warnings：无法确定或需要人工复核的点
                            示例：
                            输入"晚归 30 分钟，第一次" → {"violationType":"晚归","severity":"低","reason":"初次晚归且时间较短","suggestion":"记录并提醒按时归宿","warnings":[]}
                            只输出 JSON。
                            """.formatted(VIOLATION_TYPES))
                    .user(text)
                    .call()
                    .entity(DormAssistantDrafts.ViolationDraft.class);
        } catch (Exception e) {
            throw new BusinessApiException(500, "违规分类失败：" + e.getMessage());
        }
    }

    public DormAssistantDrafts.ImportAnalysis analyzeImportErrors(String errorText) {
        if (errorText == null || errorText.isBlank()) {
            throw new BusinessApiException(400, "请提供导入失败明细");
        }
        try {
            return chatClient.prompt()
                    .system("""
                            你是宿舍系统数据导入助手。用户会提供 Excel 导入失败明细（可能包含重复学号、床位冲突、
                            字段格式错误、必填缺失等）。请输出：
                            - summary：一句话总体结论（多少条、主要问题）
                            - causes：按数据归因的问题列表（每条说明原因，例如"床位冲突：同一房间同一床位被两个学生占用"）
                            - suggestions：可执行的修复步骤（按优先级，具体到操作，例如"将 2023xxxx 的床位由 301-2 改为 301-3 后重新导入"）
                            只输出 JSON。
                            """)
                    .user(errorText)
                    .call()
                    .entity(DormAssistantDrafts.ImportAnalysis.class);
        } catch (Exception e) {
            throw new BusinessApiException(500, "导入归因失败：" + e.getMessage());
        }
    }
}
