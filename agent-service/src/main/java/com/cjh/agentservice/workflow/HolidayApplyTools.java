package com.cjh.agentservice.workflow;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 节假日配置 · 落库工具（写操作）。
 * 仅在 UI 端显式确认（allowWrites=true）时才会挂载；调用者 JWT 绑定在实例上，模型无法伪造身份。
 */
public class HolidayApplyTools {

    private final HolidayConfigService holidayConfigService;
    private final ObjectMapper objectMapper;
    private final String authorizationHeader;

    public HolidayApplyTools(HolidayConfigService holidayConfigService, ObjectMapper objectMapper,
                             String authorizationHeader) {
        this.holidayConfigService = holidayConfigService;
        this.objectMapper = objectMapper;
        this.authorizationHeader = authorizationHeader;
    }

    @Tool(name = "applyHolidayConfig", description = "把已预览的节假日配置写入系统（管理员权限）。"
            + "必须使用 previewHolidayConfig 返回的 draftToken；该工具仅在用户于界面确认后可用。")
    public String applyHolidayConfig(@ToolParam(description = "previewHolidayConfig 返回的 draftToken") String draftToken) {
        return json(holidayConfigService.apply(draftToken, authorizationHeader));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }
}
