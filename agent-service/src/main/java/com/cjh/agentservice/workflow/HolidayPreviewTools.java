package com.cjh.agentservice.workflow;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 节假日配置 · 预览工具（只读解析，不写库，任何场景都可挂载）。
 */
public class HolidayPreviewTools {

    private final HolidayConfigService holidayConfigService;
    private final ObjectMapper objectMapper;

    public HolidayPreviewTools(HolidayConfigService holidayConfigService, ObjectMapper objectMapper) {
        this.holidayConfigService = holidayConfigService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "previewHolidayConfig", description = "把自然语言描述（如“国庆 1-7 号放假、10.11 补周三课”）解析为节假日配置草稿，"
            + "返回 draftToken、放假区间与校验警告。该操作只生成预览，不会写入系统。")
    public String previewHolidayConfig(@ToolParam(description = "自然语言描述的放假/调休安排") String text) {
        return json(holidayConfigService.preview(text));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }
}
