package com.cjh.agentservice.tools.paiban;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.cjh.agentservice.business.JwtPeek;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能排班系统 Agent 工具集。
 * 设计原则：工具只读、输出裁剪（不含 password 等敏感字段）、日期与周次由确定性代码计算。
 */
public class PaibanTools {

    private static final String[] WEEKDAY_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final BusinessClient client;
    private final String token;
    private final ObjectMapper mapper;

    private Map<Long, String> slotLabels;

    public PaibanTools(BusinessClient client, String token, ObjectMapper mapper) {
        this.client = client;
        this.token = token;
        this.mapper = mapper;
    }

    // ==================== 周次与基础信息 ====================

    @Tool(name = "currentWeek", description = "获取当前学期与教学周信息（第几周、本周起止日期）。"
            + "所有涉及‘本周/这周/上周/下周/本学期’的问题都应先调用本工具获取周次与日期，不要自行推算。")
    public String currentWeek() {
        WeekInfo week = weekInfo(null);
        return json(Map.of(
                "semesterName", week.semesterName(),
                "semesterStartDate", week.semesterStartDate(),
                "totalWeeks", week.totalWeeks(),
                "currentWeek", week.week(),
                "weekStart", week.start().toString(),
                "weekEnd", week.end().toString(),
                "today", LocalDate.now().toString()));
    }

    @Tool(name = "mySchedule", description = "查询当前登录人自己的值班排班。可传日期范围（yyyy-MM-dd），不传则默认本周。")
    public String mySchedule(
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd，默认本周一") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd，默认本周日") String endDate) {
        Map<String, String> params = rangeOrDefault(startDate, endDate);
        return json(result("GET /api/schedules/my", compactSchedules(data("/api/schedules/my", params))));
    }

    @Tool(name = "locationSchedule", description = "按地点查询某栋宿舍楼或某个办公室的排班表。"
            + "locationName 传名称（如 A1栋、行政楼201），type 传 dormitory(宿舍) 或 office(办公室)。")
    public String locationSchedule(
            @ToolParam(description = "地点名称，如 A1栋 或 行政楼201") String locationName,
            @ToolParam(description = "类型：dormitory 或 office") String type,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd，默认本周一") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd，默认本周日") String endDate) {
        long locationId = resolveLocation(type, locationName);
        Map<String, String> params = new LinkedHashMap<>(rangeOrDefault(startDate, endDate));
        params.put("type", type);
        params.put("locationId", String.valueOf(locationId));
        return json(result("GET /api/schedules", Map.of(
                "location", locationName,
                "type", type,
                "schedules", compactSchedules(data("/api/schedules", params)))));
    }

    @Tool(name = "scheduleAnalysis", description = "分析某人某一周为什么没有排班：综合本周与上一周排班、冷却期、请假、空闲时间、多排状态，"
            + "给出结构化原因。userKeyword 为空时分析当前登录人；weekNumber 为空时分析本周。")
    public String scheduleAnalysis(
            @ToolParam(required = false, description = "用户姓名或用户名，默认当前登录人") String userKeyword,
            @ToolParam(required = false, description = "教学周次（数字），默认本周") Integer weekNumber) {
        WeekInfo week = weekInfo(weekNumber);
        JsonNode target = resolveUser(userKeyword);
        long userId = target.path("id").asLong();
        String userName = target.path("realName").asText("");

        Map<String, String> currentParams = Map.of("startDate", week.start().toString(), "endDate", week.end().toString());
        List<Map<String, Object>> current = compactSchedules(data("/api/schedules/all", currentParams)).stream()
                .filter(row -> userId == ((Number) row.get("userId")).longValue())
                .toList();
        LocalDate prevStart = week.start().minusWeeks(1);
        LocalDate prevEnd = week.end().minusWeeks(1);
        List<Map<String, Object>> previous = compactSchedules(data("/api/schedules/all", Map.of(
                        "startDate", prevStart.toString(), "endDate", prevEnd.toString()))).stream()
                .filter(row -> userId == ((Number) row.get("userId")).longValue())
                .toList();
        List<Map<String, Object>> leaves = new ArrayList<>();
        for (JsonNode leave : data("/api/leaves", Map.of())) {
            if (leave.path("userId").asLong() == userId
                    && "approved".equals(leave.path("status").asText())
                    && leave.path("startDate").asText().compareTo(week.end().toString()) <= 0
                    && leave.path("endDate").asText().compareTo(week.start().toString()) >= 0) {
                leaves.add(Map.of(
                        "startDate", leave.path("startDate").asText(),
                        "endDate", leave.path("endDate").asText(),
                        "leaveType", leave.path("leaveType").asText(),
                        "reason", leave.path("reason").asText(),
                        "status", leave.path("status").asText()));
            }
        }
        List<Map<String, Object>> availability = compactAvailability(data("/api/availabilities/user/" + userId, Map.of()));
        List<Map<String, Object>> multiDuty = new ArrayList<>();
        try {
            for (JsonNode multi : data("/api/multi-duty", Map.of("status", "approved"))) {
                if (multi.path("userId").asLong() == userId
                        && multi.path("weekStart").asText().compareTo(week.end().toString()) <= 0
                        && multi.path("weekEnd").asText().compareTo(week.start().toString()) >= 0) {
                    multiDuty.add(Map.of(
                            "weekStart", multi.path("weekStart").asText(),
                            "weekEnd", multi.path("weekEnd").asText(),
                            "note", multi.path("note").asText()));
                }
            }
        } catch (BusinessApiException ignored) {
            // 非管理员无法查看全量多排列表，忽略
        }

        boolean coolingSuspected = !previous.isEmpty() && current.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "GET /api/schedules/all + /api/leaves + /api/availabilities/user/{id} + /api/multi-duty");
        result.put("week", week.week());
        result.put("weekRange", week.start() + " ~ " + week.end());
        result.put("user", userName.isBlank() ? userId : userName);
        result.put("thisWeekDuty", current);
        result.put("previousWeekDuty", previous);
        result.put("coolingSuspected", coolingSuspected);
        result.put("approvedLeaves", leaves);
        result.put("availability", availability);
        result.put("approvedMultiDuty", multiDuty);
        if (coolingSuspected) {
            result.put("conclusion", "该用户上周有排班、本周没有：处于‘排班冷却期’（防连排机制），系统不会连续两周安排同一人。");
        } else if (!leaves.isEmpty()) {
            result.put("conclusion", "该用户本周有已批准请假，与排班日期冲突，因此未排班。");
        } else if (current.isEmpty() && previous.isEmpty()) {
            result.put("conclusion", "该用户本周与上周均无排班：可能是空闲时间不匹配或轮转未轮到，请结合空闲时间判断。");
        } else {
            result.put("conclusion", "该用户本周已有排班。");
        }
        return json(result);
    }

    // ==================== 统计与总览 ====================

    @Tool(name = "dutyCounts", description = "按人员统计值班次数（可按类型与日期范围筛选）。仅管理员可用；"
            + "不传日期时统计今天之前的数据。")
    public String dutyCounts(
            @ToolParam(required = false, description = "类型：dormitory 或 office，默认全部") String type,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd") String endDate) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "type", type);
        putIfPresent(params, "startDate", startDate);
        putIfPresent(params, "endDate", endDate);
        return json(result("GET /api/statistics/duty-counts", data("/api/statistics/duty-counts", params)));
    }

    @Tool(name = "adminOverview", description = "管理员总览：楼栋数、办公室数、人员数、待审批请假数、本周值班数、消息数。仅管理员可用。")
    public String adminOverview() {
        return json(result("GET /api/statistics/admin", data("/api/statistics/admin", Map.of())));
    }

    // ==================== 请假与假期 ====================

    @Tool(name = "pendingLeaves", description = "查看待审批的请假申请（最新 5 条，含申请人、类型、日期、事由）。仅管理员可用。")
    public String pendingLeaves() {
        return json(result("GET /api/leaves/pending", compactLeaves(data("/api/leaves/pending", Map.of()))));
    }

    @Tool(name = "leaveList", description = "查看请假申请列表：普通用户仅返回自己的，管理员返回全部。")
    public String leaveList() {
        return json(result("GET /api/leaves", compactLeaves(data("/api/leaves", Map.of()))));
    }

    @Tool(name = "holidayList", description = "查看节假日安排（放假起止日期），用于判断某天是否需要排班。")
    public String holidayList() {
        return json(result("GET /api/holidays", data("/api/holidays", Map.of())));
    }

    // ==================== 空闲时间 ====================

    @Tool(name = "myAvailability", description = "查询当前登录人自己的空闲时间（单双周、星期几、时段）。")
    public String myAvailability() {
        return json(result("GET /api/availabilities/my", compactAvailability(data("/api/availabilities/my", Map.of()))));
    }

    @Tool(name = "userAvailability", description = "查询指定用户的空闲时间。userId 为用户 ID（可先用 userLookup 查 ID）。")
    public String userAvailability(@ToolParam(description = "用户 ID") long userId) {
        return json(result("GET /api/availabilities/user/" + userId,
                compactAvailability(data("/api/availabilities/user/" + userId, Map.of()))));
    }

    // ==================== 换班 / 多排 ====================

    @Tool(name = "mySwapRequests", description = "查询当前登录人自己的换班申请及状态。")
    public String mySwapRequests() {
        return json(result("GET /api/swap-requests/my", compactRows(data("/api/swap-requests/my", Map.of()))));
    }

    @Tool(name = "swapRequestList", description = "查看换班申请列表（可按 pending/approved/rejected/done 筛选）。仅管理员可用。")
    public String swapRequestList(@ToolParam(required = false, description = "状态：pending/approved/rejected/done") String status) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "status", status);
        return json(result("GET /api/swap-requests", compactRows(data("/api/swap-requests", params))));
    }

    @Tool(name = "myMultiDuty", description = "查询当前登录人自己的多排申请及状态。")
    public String myMultiDuty() {
        return json(result("GET /api/multi-duty/my", compactRows(data("/api/multi-duty/my", Map.of()))));
    }

    @Tool(name = "multiDutyList", description = "查看多排申请列表（可按 pending/approved/rejected 筛选）。仅管理员可用。")
    public String multiDutyList(@ToolParam(required = false, description = "状态：pending/approved/rejected") String status) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "status", status);
        return json(result("GET /api/multi-duty", compactRows(data("/api/multi-duty", params))));
    }

    // ==================== 纪律 / 消息 / 用户 ====================

    @Tool(name = "disciplineRecords", description = "查询值班纪律记录（迟到/缺岗，可按类型与日期筛选）。仅管理员可用。")
    public String disciplineRecords(
            @ToolParam(required = false, description = "记录类型：late 或 absent") String recordType,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd") String endDate) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "recordType", recordType);
        putIfPresent(params, "startDate", startDate);
        putIfPresent(params, "endDate", endDate);
        return json(result("GET /api/discipline", compactRows(data("/api/discipline", params))));
    }

    @Tool(name = "messageList", description = "查看系统消息/通知列表（标题、类型、发送人、时间、是否已读）。")
    public String messageList() {
        return json(result("GET /api/messages", compactMessages(data("/api/messages", Map.of()))));
    }

    @Tool(name = "userLookup", description = "按姓名/用户名/班级/部门模糊查询用户（返回 ID 与角色，用于其他工具的 userId）。仅管理员可用。")
    public String userLookup(@ToolParam(description = "关键词，如 张三") String keyword) {
        return json(result("GET /api/users?keyword=", compactUsers(data("/api/users", Map.of("keyword", keyword)))));
    }

    @Tool(name = "staffWithCounts", description = "查询可排班人员及其值班统计、请假区间、已批准多排、空闲时间。"
            + "type 传 dormitory 或 office。仅管理员可用，适合排班分析与人员不足排查。")
    public String staffWithCounts(@ToolParam(description = "类型：dormitory 或 office") String type) {
        return json(result("GET /api/users/staff-with-counts", data("/api/users/staff-with-counts", Map.of("type", type))));
    }

    // ==================== 内部实现 ====================

    private JsonNode data(String path, Map<String, String> params) {
        return client.get(token, path, params);
    }

    private JsonNode resolveUser(String userKeyword) {
        if (userKeyword == null || userKeyword.isBlank()) {
            JwtPeek.Caller caller = JwtPeek.parse(token);
            if (caller.userId() == null) {
                throw new BusinessApiException(401, "无法识别当前登录人，请指定用户姓名");
            }
            return mapper.valueToTree(Map.of("id", caller.userId(),
                    "realName", caller.username() == null ? "" : caller.username()));
        }
        if (userKeyword.matches("\\d+")) {
            try {
                JsonNode byId = data("/api/users/" + userKeyword, Map.of());
                if (byId.hasNonNull("id")) {
                    return byId;
                }
            } catch (BusinessApiException ignored) {
                // 数字 ID 未命中，继续按关键词模糊匹配
            }
        }
        JsonNode users = data("/api/users", Map.of("keyword", userKeyword));
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode user : users) {
            String realName = user.path("realName").asText("");
            String username = user.path("username").asText("");
            if (realName.equals(userKeyword) || username.equals(userKeyword)) {
                return user;
            }
            if (realName.contains(userKeyword) || username.contains(userKeyword)) {
                matches.add(user);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new BusinessApiException(404, "没有找到匹配的用户：" + userKeyword);
        }
        StringBuilder names = new StringBuilder();
        for (JsonNode match : matches) {
            if (!names.isEmpty()) {
                names.append("、");
            }
            names.append(match.path("realName").asText("")).append("(").append(match.path("id").asLong()).append(")");
        }
        throw new BusinessApiException(400, "匹配到多个用户：" + names + "，请使用更精确的姓名");
    }

    private long resolveLocation(String type, String locationName) {
        if (type == null || type.isBlank()) {
            throw new BusinessApiException(400, "请指定类型：dormitory 或 office");
        }
        String path = "office".equalsIgnoreCase(type) ? "/api/offices" : "/api/dormitories";
        JsonNode locations = data(path, Map.of());
        String input = normalizeLocation(locationName);
        List<JsonNode> matched = new ArrayList<>();
        for (JsonNode location : locations) {
            String name = normalizeLocation(location.path("name").asText(""));
            String code = normalizeLocation(location.path("code").asText(""));
            if (name.equals(input) || code.equals(input)) {
                return location.path("id").asLong();
            }
            if (name.contains(input) || input.contains(name)) {
                matched.add(location);
            } else if (code.length() >= 2 && input.contains(code)) {
                matched.add(location);
            }
        }
        if (matched.size() == 1) {
            return matched.get(0).path("id").asLong();
        }
        if (matched.isEmpty()) {
            throw new BusinessApiException(404, "没有找到地点：" + locationName + "，请确认名称（可先查询宿舍楼/办公室列表）");
        }
        StringBuilder names = new StringBuilder();
        for (JsonNode match : matched) {
            names.append(match.path("name").asText("")).append("；");
        }
        throw new BusinessApiException(400, "地点不唯一，匹配到：" + names + "请使用完整名称");
    }

    private String normalizeLocation(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private WeekInfo weekInfo(Integer weekNumber) {
        JsonNode semester = data("/api/semester", Map.of());
        if (semester.isMissingNode() || semester.isNull() || semester.path("startDate").asText().isBlank()) {
            throw new BusinessApiException(500, "系统尚未配置学期，无法计算教学周");
        }
        LocalDate semesterStart = LocalDate.parse(semester.path("startDate").asText());
        LocalDate anchor = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int totalWeeks = semester.path("totalWeeks").asInt(20);
        int week = weekNumber != null ? weekNumber
                : (int) (java.time.temporal.ChronoUnit.WEEKS.between(anchor, mondayOfThisWeek()) + 1);
        LocalDate start = anchor.plusWeeks(week - 1L);
        return new WeekInfo(week, start, start.plusDays(6), semester.path("name").asText(""), semesterStart.toString(), totalWeeks);
    }

    private LocalDate mondayOfThisWeek() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Map<String, String> rangeOrDefault(String startDate, String endDate) {
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("startDate", startDate);
            params.put("endDate", endDate);
            return params;
        }
        WeekInfo week = weekInfo(null);
        return Map.of("startDate", week.start().toString(), "endDate", week.end().toString());
    }

    private void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private List<Map<String, Object>> compactSchedules(JsonNode rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", row.path("userId").asLong());
            item.put("userName", row.path("userName").asText(""));
            item.put("date", row.path("dutyDate").asText(""));
            item.put("weekday", weekdayCn(row.path("dutyDate").asText("")));
            item.put("type", "office".equals(row.path("type").asText()) ? "办公室" : "宿舍");
            item.put("location", row.path("locationName").asText(""));
            item.put("timeSlot", row.path("timeSlot").asText(""));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> compactLeaves(JsonNode rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.path("id").asLong());
            item.put("userName", row.path("userName").asText(""));
            item.put("dutyType", row.path("dutyType").asText(""));
            item.put("leaveType", row.path("leaveType").asText(""));
            item.put("startDate", row.path("startDate").asText(""));
            item.put("endDate", row.path("endDate").asText(""));
            item.put("days", row.path("days").asInt());
            item.put("reason", row.path("reason").asText(""));
            item.put("status", row.path("status").asText(""));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> compactAvailability(JsonNode rows) {
        Map<Long, String> labels = slotLabels();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weekParity", parityCn(row.path("weekParity").asText("")));
            item.put("weekday", weekdayCnByNumber(row.path("dayOfWeek").asInt(1)));
            item.put("timeSlot", labels.getOrDefault(row.path("timeSlotId").asLong(), "时段#" + row.path("timeSlotId").asLong()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> compactRows(JsonNode rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            row.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                if ("attachment".equals(name) || "handledBy".equals(name)) {
                    return;
                }
                JsonNode value = entry.getValue();
                item.put(name, value.isNumber() ? value.numberValue()
                        : value.isBoolean() ? value.booleanValue() : value.asText(""));
            });
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> compactMessages(JsonNode rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.path("id").asLong());
            item.put("title", row.path("title").asText(""));
            item.put("type", row.path("type").asText(""));
            item.put("senderName", row.path("senderName").asText(""));
            item.put("createTime", row.path("createTime").asText(""));
            item.put("isRead", row.path("isRead").asInt(1) == 1);
            String content = row.path("content").asText("");
            item.put("content", content.length() > 120 ? content.substring(0, 120) + "…" : content);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> compactUsers(JsonNode rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.path("id").asLong());
            item.put("username", row.path("username").asText(""));
            item.put("realName", row.path("realName").asText(""));
            item.put("role", row.path("role").asText(""));
            item.put("dutyRole", row.path("dutyRole").asText(""));
            item.put("className", row.path("className").asText(""));
            item.put("department", row.path("department").asText(""));
            item.put("status", row.path("status").asInt(1));
            result.add(item);
        }
        return result;
    }

    private Map<Long, String> slotLabels() {
        if (slotLabels == null) {
            Map<Long, String> labels = new LinkedHashMap<>();
            for (JsonNode slot : data("/api/time-slots", Map.of())) {
                labels.put(slot.path("id").asLong(), slot.path("label").asText(""));
            }
            slotLabels = labels;
        }
        return slotLabels;
    }

    private Map<String, Object> result(String source, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("data", data);
        return result;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }

    private String weekdayCn(String date) {
        try {
            return WEEKDAY_CN[LocalDate.parse(date).getDayOfWeek().getValue() - 1];
        } catch (Exception e) {
            return "";
        }
    }

    private String weekdayCnByNumber(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? WEEKDAY_CN[dayOfWeek - 1] : "未知";
    }

    private String parityCn(String parity) {
        return switch (parity == null ? "" : parity) {
            case "odd" -> "单周";
            case "even" -> "双周";
            case "both" -> "单双周均可";
            default -> parity;
        };
    }

    private record WeekInfo(int week, LocalDate start, LocalDate end, String semesterName,
                            String semesterStartDate, int totalWeeks) {
    }
}
