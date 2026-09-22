package com.cjh.agentservice.workflow;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.cjh.agentservice.business.JwtPeek;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自然语言 → 节假日/调休结构化配置：解析 → 校验 → 生成预览 → 管理员确认后写库（两阶段，LLM 无法直接落库）。
 */
@Service
public class HolidayConfigService {

    private static final Logger log = LoggerFactory.getLogger(HolidayConfigService.class);
    private static final String[] WEEKDAY_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final long DRAFT_TTL_MS = 10 * 60 * 1000L;

    private final ChatClient chatClient;
    private final BusinessClient businessClient;
    private final ObjectMapper objectMapper;

    private final Map<String, DraftEntry> drafts = new ConcurrentHashMap<>();

    public HolidayConfigService(@Lazy ChatClient chatClient, BusinessClient businessClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.businessClient = businessClient;
        this.objectMapper = objectMapper;
    }

    private record DraftEntry(HolidayDraft draft, Instant createdAt) {
    }

    public Map<String, Object> preview(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessApiException(400, "请提供要解析的节假日安排文本");
        }
        HolidayDraft draft;
        try {
            LocalDate today = LocalDate.now();
            draft = chatClient.prompt()
                    .system("""
                            你是节假日配置解析器。今天是 %s（当前年份 %d）。
                            把用户给出的放假与调休安排解析为结构化 JSON：
                            - holidays：放假区间（name、startDate、endDate，日期格式 yyyy-MM-dd；用户未写年份时必须使用当前年份 %d）
                            - makeups：调休补课（makeupDate 补课日期、replacesDate 被替换的工作日——通常是放假区间中被冲掉的某个工作日，无法确定时留空字符串并写入 warnings、note 说明）
                            - summary：一句话摘要
                            - warnings：无法确定或需要人工确认的点
                            只输出 JSON，不要输出解释。
                            """.formatted(today, today.getYear(), today.getYear()))
                    .user(text)
                    .call()
                    .entity(HolidayDraft.class);
        } catch (Exception e) {
            throw new BusinessApiException(500, "解析失败：" + e.getMessage());
        }
        List<String> warnings = new ArrayList<>(draft.warnings() == null ? List.of() : draft.warnings());
        List<HolidayDraft.HolidayItem> holidays = draft.holidays() == null ? List.of() : draft.holidays();
        if (holidays.isEmpty()) {
            warnings.add("未解析出任何放假区间，请检查输入文本");
        }
        for (HolidayDraft.HolidayItem holiday : holidays) {
            try {
                LocalDate start = LocalDate.parse(holiday.startDate());
                LocalDate end = LocalDate.parse(holiday.endDate());
                if (end.isBefore(start)) {
                    warnings.add(holiday.name() + "：结束日期早于开始日期");
                }
                long days = end.toEpochDay() - start.toEpochDay() + 1;
                if (days > 15) {
                    warnings.add(holiday.name() + "：放假天数达 " + days + " 天，请确认是否输入有误");
                }
            } catch (Exception e) {
                warnings.add(holiday.name() + "：日期格式无法解析（" + holiday.startDate() + " ~ " + holiday.endDate() + "）");
            }
        }
        for (HolidayDraft.MakeupItem makeup : draft.makeups() == null ? List.<HolidayDraft.MakeupItem>of() : draft.makeups()) {
            try {
                DayOfWeek makeupDay = LocalDate.parse(makeup.makeupDate()).getDayOfWeek();
                DayOfWeek replacedDay = LocalDate.parse(makeup.replacesDate()).getDayOfWeek();
                if (makeupDay != replacedDay) {
                    warnings.add("调休 " + makeup.makeupDate() + "（" + WEEKDAY_CN[makeupDay.getValue() - 1]
                            + "）与被替换日 " + makeup.replacesDate() + "（" + WEEKDAY_CN[replacedDay.getValue() - 1]
                            + "）星期不一致，课表将按被替换日属性排班，请人工确认");
                }
            } catch (Exception e) {
                warnings.add("调休日期格式无法解析：" + makeup.makeupDate() + " / " + makeup.replacesDate());
            }
        }

        String draftToken = UUID.randomUUID().toString().replace("-", "");
        HolidayDraft normalized = new HolidayDraft(
                draft.summary() == null ? "节假日配置" : draft.summary(),
                holidays,
                draft.makeups() == null ? List.of() : draft.makeups(),
                warnings);
        drafts.put(draftToken, new DraftEntry(normalized, Instant.now()));
        purgeExpired();
        log.info("生成节假日配置草稿 {}：{} 个假期、{} 条调休、{} 条警告",
                draftToken, holidays.size(), normalized.makeups().size(), warnings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draftToken", draftToken);
        result.put("summary", normalized.summary());
        result.put("holidays", normalized.holidays());
        result.put("makeups", normalized.makeups());
        result.put("warnings", normalized.warnings());
        result.put("notice", "这是预览，尚未写入系统。请管理员确认后调用 applyHolidayConfig（或界面确认按钮）落库。");
        return result;
    }

    public Map<String, Object> apply(String draftToken, String authorizationHeader) {
        DraftEntry entry = drafts.get(draftToken);
        if (entry == null) {
            throw new BusinessApiException(404, "草稿不存在或已过期，请重新生成预览");
        }
        if (Instant.now().isAfter(entry.createdAt().plusMillis(DRAFT_TTL_MS))) {
            drafts.remove(draftToken);
            throw new BusinessApiException(410, "草稿已过期（超过 10 分钟），请重新生成预览");
        }
        String token = extractToken(authorizationHeader);
        JwtPeek.Caller caller = JwtPeek.parse(token);
        if (!"admin".equals(caller.role())) {
            throw new BusinessApiException(403, "仅管理员可以确认落库");
        }
        if (entry.draft().holidays().isEmpty()) {
            throw new BusinessApiException(400, "草稿中没有可写入的假期");
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (HolidayDraft.HolidayItem holiday : entry.draft().holidays()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", holiday.name());
            body.put("startDate", holiday.startDate());
            body.put("endDate", holiday.endDate());
            var data = businessClient.post(token, "/api/holidays", body);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", holiday.name());
            row.put("startDate", holiday.startDate());
            row.put("endDate", holiday.endDate());
            if (data.hasNonNull("id")) {
                row.put("id", data.path("id").asLong());
            }
            created.add(row);
        }
        drafts.remove(draftToken);
        log.info("节假日配置已落库：{} 条", created.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("makeupsSkipped", entry.draft().makeups().size());
        result.put("note", entry.draft().makeups().isEmpty()
                ? "假期已写入系统"
                : "假期已写入系统；调休补课映射暂未自动写入，请在学期调整中人工维护（已提示管理员）");
        return result;
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessApiException(401, "确认落库需要管理员登录态");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        drafts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().createdAt().plusMillis(DRAFT_TTL_MS)));
    }
}
