package com.cjh.agentservice.demo;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示模式数据源：合成数据，不依赖任何外部系统。
 * 与真实版共用同一套 @Tool，保证 demo 可独立运行/展示，避免使用学校项目数据。
 */
public class DemoBusinessClient implements BusinessClient {

    private static final long DEMO_USER_ID = 1L;

    private final ObjectMapper mapper;
    private final LocalDate anchor;
    private final LocalDate week1Monday;

    public DemoBusinessClient(ObjectMapper mapper) {
        this.mapper = mapper;
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        this.week1Monday = thisMonday.minusWeeks(10);
        this.anchor = week1Monday;
    }

    @Override
    public JsonNode get(String token, String path, Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;

        if (path.equals("/api/semester")) {
            return tree(semester());
        }
        if (path.equals("/api/time-slots")) {
            return tree(timeSlots());
        }
        if (path.equals("/api/dormitories")) {
            return tree(dormitories());
        }
        if (path.equals("/api/offices")) {
            return tree(offices());
        }
        if (path.equals("/api/schedules/my")) {
            return tree(filterSchedules(p, DEMO_USER_ID));
        }
        if (path.equals("/api/schedules/all")) {
            return tree(filterSchedules(p, null));
        }
        if (path.equals("/api/schedules")) {
            return tree(filterSchedules(p, null));
        }
        if (path.equals("/api/schedules/office/week-columns")) {
            return tree(officeWeekColumns(p));
        }
        if (path.equals("/api/statistics/admin")) {
            return tree(adminOverview());
        }
        if (path.equals("/api/statistics/duty-counts")) {
            return tree(dutyCounts());
        }
        if (path.equals("/api/statistics/pending-leaves")) {
            return tree(pendingLeaves());
        }
        if (path.equals("/api/statistics/recent-messages")) {
            return tree(messages());
        }
        if (path.equals("/api/leaves")) {
            return tree(leaves());
        }
        if (path.equals("/api/leaves/pending")) {
            return tree(pendingLeaves());
        }
        if (path.equals("/api/holidays")) {
            return tree(holidays());
        }
        if (path.equals("/api/availabilities/my")) {
            return tree(availabilities(DEMO_USER_ID));
        }
        if (path.startsWith("/api/availabilities/user/")) {
            long userId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            return tree(availabilities(userId));
        }
        if (path.equals("/api/availabilities/all")) {
            return tree(allAvailabilities());
        }
        if (path.equals("/api/swap-requests/my")) {
            return tree(filterByStatus(swapRequests(), null, DEMO_USER_ID));
        }
        if (path.equals("/api/swap-requests")) {
            return tree(filterByStatus(swapRequests(), p.get("status"), null));
        }
        if (path.equals("/api/multi-duty/my")) {
            return tree(filterByStatus(multiDuty(), null, DEMO_USER_ID));
        }
        if (path.equals("/api/multi-duty")) {
            return tree(filterByStatus(multiDuty(), p.get("status"), null));
        }
        if (path.equals("/api/discipline")) {
            return tree(disciplineRecords(p));
        }
        if (path.equals("/api/discipline/actions")) {
            return tree(disciplineActions(p));
        }
        if (path.equals("/api/messages")) {
            return tree(messages());
        }
        if (path.equals("/api/duty-adjustments")) {
            return tree(dutyAdjustments());
        }
        if (path.equals("/api/history/weeks")) {
            return tree(weeks());
        }
        if (path.equals("/api/users/staff")) {
            return tree(staff());
        }
        if (path.equals("/api/users/staff-with-counts")) {
            return tree(staffWithCounts());
        }
        if (path.equals("/api/users")) {
            String keyword = p.get("keyword");
            if (keyword == null || keyword.isBlank()) {
                return tree(users());
            }
            return tree(users().stream()
                    .filter(u -> String.valueOf(u.get("realName")).contains(keyword)
                            || String.valueOf(u.get("username")).contains(keyword))
                    .toList());
        }
        if (path.startsWith("/api/users/")) {
            long userId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            return tree(users().stream()
                    .filter(u -> ((Number) u.get("id")).longValue() == userId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessApiException(404, "用户不存在（演示数据）")));
        }
        throw new BusinessApiException(404, "演示模式暂不支持接口：" + path);
    }

    @Override
    public JsonNode post(String token, String path, Object body) {
        throw new BusinessApiException(500, "演示模式为只读，不支持写操作：" + path);
    }

    @Override
    public JsonNode put(String token, String path, Object body) {
        throw new BusinessApiException(500, "演示模式为只读，不支持写操作：" + path);
    }

    @Override
    public JsonNode delete(String token, String path) {
        throw new BusinessApiException(500, "演示模式为只读，不支持写操作：" + path);
    }

    @Override
    public JsonNode delete(String token, String path, Object body) {
        throw new BusinessApiException(500, "演示模式为只读，不支持写操作：" + path);
    }

    private JsonNode tree(Object value) {
        return mapper.valueToTree(value);
    }

    // ==================== 基础数据 ====================

    private Map<String, Object> semester() {
        return Map.of(
                "id", 1,
                "name", "2026-2027学年第一学期（演示数据）",
                "startDate", week1Monday.toString(),
                "totalWeeks", 20);
    }

    private List<Map<String, Object>> timeSlots() {
        return List.of(
                slot(1, "巡班(19:00-21:00)", "19:00", "21:00", "晚"),
                slot(2, "坐班(14:00-17:00)", "14:00", "17:00", "下午"),
                slot(3, "敲灯(22:00-22:30)", "22:00", "22:30", "晚"),
                slot(4, "1-2节", "08:00", "09:40", "上午"),
                slot(5, "3-4节", "10:00", "11:40", "上午"));
    }

    private Map<String, Object> slot(int id, String label, String start, String end, String period) {
        return Map.of("id", id, "label", label, "startTime", start, "endTime", end,
                "period", period, "sortOrder", id, "status", 1);
    }

    private List<Map<String, Object>> dormitories() {
        return List.of(
                Map.of("id", 1, "code", "A1", "name", "A1 宿舍楼", "building", "A区", "floor", 6,
                        "description", "男生宿舍（演示）", "gender", "男", "status", 1),
                Map.of("id", 2, "code", "B2", "name", "B2 宿舍楼", "building", "B区", "floor", 6,
                        "description", "女生宿舍（演示）", "gender", "女", "status", 1));
    }

    private List<Map<String, Object>> offices() {
        return List.of(
                Map.of("id", 1, "code", "OFF1", "name", "行政楼201", "building", "行政楼", "floor", 2,
                        "description", "办公室（演示）", "status", 1),
                Map.of("id", 2, "code", "OFF2", "name", "图书馆301", "building", "图书馆", "floor", 3,
                        "description", "办公室（演示）", "status", 1));
    }

    private List<Map<String, Object>> users() {
        return List.of(
                user(1, "demo1", "演示用户A", "user", "巡班", "演示班1", "组织部"),
                user(2, "demo2", "演示用户B", "user", "坐班", "演示班2", "宣传部"),
                user(3, "demo3", "演示用户C", "user", "敲灯", "演示班3", "实践部"),
                user(9, "admin", "演示管理员", "admin", "巡班", "演示班1", "组织部"));
    }

    private Map<String, Object> user(long id, String username, String realName, String role,
                                     String dutyRole, String className, String department) {
        return Map.of("id", id, "username", username, "realName", realName, "role", role,
                "dutyRole", dutyRole, "className", className, "department", department,
                "gender", id % 2 == 0 ? "女" : "男", "status", 1);
    }

    private List<Map<String, Object>> staff() {
        return users();
    }

    // ==================== 排班数据 ====================

    private List<Map<String, Object>> schedules() {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate monday = week1Monday.plusWeeks(10);
        // 本周排班
        rows.add(duty(1, "演示用户A", "dormitory", 1, "A1栋", monday, 1, "巡班(19:00-21:00)"));
        rows.add(duty(1, "演示用户A", "dormitory", 1, "A1栋", monday.plusDays(2), 1, "巡班(19:00-21:00)"));
        rows.add(duty(2, "演示用户B", "dormitory", 1, "A1栋", monday.plusDays(1), 2, "坐班(14:00-17:00)"));
        rows.add(duty(2, "演示用户B", "office", 1, "行政楼201", monday.plusDays(1), 2, "坐班(14:00-17:00)"));
        rows.add(duty(2, "演示用户B", "dormitory", 1, "A1栋", monday.plusDays(3), 1, "巡班(19:00-21:00)"));
        // 上一周排班（用于冷却分析：用户C 上周有排班、本周无 → 处于冷却期）
        LocalDate prevMonday = monday.minusWeeks(1);
        rows.add(duty(3, "演示用户C", "dormitory", 2, "B2栋", prevMonday.plusDays(1), 1, "巡班(19:00-21:00)"));
        rows.add(duty(3, "演示用户C", "dormitory", 2, "B2栋", prevMonday.plusDays(3), 1, "巡班(19:00-21:00)"));
        rows.add(duty(1, "演示用户A", "dormitory", 1, "A1栋", prevMonday, 2, "坐班(14:00-17:00)"));
        rows.add(duty(2, "演示用户B", "office", 2, "图书馆301", prevMonday.plusDays(2), 2, "坐班(14:00-17:00)"));
        return rows;
    }

    private Map<String, Object> duty(long userId, String userName, String type, long locationId,
                                     String locationName, LocalDate date, long timeSlotId, String timeSlotLabel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", date.toEpochDay() * 10 + userId);
        row.put("type", type);
        row.put("locationId", locationId);
        row.put("locationName", locationName);
        row.put("userId", userId);
        row.put("userName", userName);
        row.put("dutyDate", date.toString());
        row.put("timeSlotId", timeSlotId);
        row.put("timeSlot", timeSlotLabel);
        row.put("createTime", date.minusDays(3).toString() + " 10:00:00");
        row.put("updateTime", date.minusDays(3).toString() + " 10:00:00");
        return row;
    }

    private List<Map<String, Object>> filterSchedules(Map<String, String> p, Long userId) {
        String start = p.get("startDate");
        String end = p.get("endDate");
        String type = p.get("type");
        String locationId = p.get("locationId");
        return schedules().stream()
                .filter(row -> userId == null || ((Number) row.get("userId")).longValue() == userId)
                .filter(row -> start == null || ((String) row.get("dutyDate")).compareTo(start) >= 0)
                .filter(row -> end == null || ((String) row.get("dutyDate")).compareTo(end) <= 0)
                .filter(row -> type == null || type.equals(row.get("type")))
                .filter(row -> locationId == null || String.valueOf(row.get("locationId")).equals(locationId))
                .toList();
    }

    private List<Map<String, Object>> officeWeekColumns(Map<String, String> p) {
        LocalDate monday = week1Monday.plusWeeks(10);
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate date = monday.plusDays(i);
            columns.add(Map.of(
                    "date", date.toString(),
                    "weekday", date.getDayOfWeek().getValue(),
                    "isMakeup", false,
                    "makeupLabel", "",
                    "effectiveDow", date.getDayOfWeek().getValue(),
                    "effectiveParity", (10 + 1) % 2 == 1 ? "odd" : "even"));
        }
        return columns;
    }

    private List<Map<String, Object>> staffWithCounts() {
        return List.of(
                Map.of("id", 1, "realName", "演示用户A", "username", "demo1", "gender", "男",
                        "dutyRole", "巡班", "totalCount", 8,
                        "detailCounts", Map.of("巡班", 6L, "坐班", 2L),
                        "leaveRanges", List.of(), "multiDuty", List.of()),
                Map.of("id", 2, "realName", "演示用户B", "username", "demo2", "gender", "女",
                        "dutyRole", "坐班", "totalCount", 10,
                        "detailCounts", Map.of("坐班", 7L, "巡班", 3L),
                        "leaveRanges", List.of(Map.of("start", LocalDate.now().plusDays(2).toString(),
                                "end", LocalDate.now().plusDays(2).toString())),
                        "multiDuty", List.of()),
                Map.of("id", 3, "realName", "演示用户C", "username", "demo3", "gender", "男",
                        "dutyRole", "敲灯", "totalCount", 6,
                        "detailCounts", Map.of("敲灯", 4L, "巡班", 2L),
                        "leaveRanges", List.of(), "multiDuty", List.of()));
    }

    // ==================== 统计 / 请假 / 假期 ====================

    private Map<String, Object> adminOverview() {
        return Map.of(
                "dormitoryCount", 2,
                "officeCount", 2,
                "staffCount", 3,
                "pendingLeaveCount", 1,
                "weekDutyCount", 5,
                "messageCount", 2);
    }

    private List<Map<String, Object>> dutyCounts() {
        return List.of(
                Map.of("userId", 1, "userName", "演示用户A", "totalCount", 8L, "巡班", 6L, "坐班", 2L),
                Map.of("userId", 2, "userName", "演示用户B", "totalCount", 10L, "坐班", 7L, "巡班", 3L),
                Map.of("userId", 3, "userName", "演示用户C", "totalCount", 6L, "敲灯", 4L, "巡班", 2L));
    }

    private List<Map<String, Object>> leaves() {
        LocalDate today = LocalDate.now();
        return List.of(
                leave(1, 2, "演示用户B", "dormitory", "事假", today.plusDays(2).toString(), today.plusDays(2).toString(),
                        1, "课程冲突（演示）", "pending"),
                leave(2, 1, "演示用户A", "dormitory", "病假", today.plusDays(9).toString(), today.plusDays(11).toString(),
                        3, "回家就医（演示）", "approved"),
                leave(3, 3, "演示用户C", "dormitory", "事假", today.minusDays(9).toString(), today.minusDays(9).toString(),
                        1, "临时有事（演示）", "approved"));
    }

    private Map<String, Object> leave(long id, long userId, String userName, String dutyType, String leaveType,
                                      String startDate, String endDate, int days, String reason, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("userId", userId);
        row.put("userName", userName);
        row.put("dutyType", dutyType);
        row.put("leaveType", leaveType);
        row.put("startDate", startDate);
        row.put("endDate", endDate);
        row.put("days", days);
        row.put("reason", reason);
        row.put("status", status);
        row.put("createTime", startDate + " 09:00:00");
        return row;
    }

    private List<Map<String, Object>> pendingLeaves() {
        return leaves().stream().filter(row -> "pending".equals(row.get("status"))).toList();
    }

    private List<Map<String, Object>> holidays() {
        int year = LocalDate.now().getYear();
        return List.of(
                Map.of("id", 1, "name", "国庆节", "startDate", year + "-10-01", "endDate", year + "-10-07",
                        "createTime", year + "-09-01 10:00:00"),
                Map.of("id", 2, "name", "中秋节", "startDate", year + "-09-25", "endDate", year + "-09-27",
                        "createTime", year + "-09-01 10:00:00"));
    }

    private List<Map<String, Object>> dutyAdjustments() {
        int year = LocalDate.now().getYear();
        return List.of(Map.of(
                "id", 1,
                "startDate", year + "-10-01",
                "endDate", year + "-10-07",
                "note", "国庆放假调休（演示）",
                "makeups", List.of(Map.of("makeupDate", year + "-09-27", "replacedDate", year + "-10-05"))));
    }

    // ==================== 空闲 / 换班 / 多排 / 纪律 / 消息 ====================

    private List<Map<String, Object>> availabilities(long userId) {
        return List.of(
                availability(1, userId, "odd", 1, 1),
                availability(2, userId, "odd", 3, 1),
                availability(3, userId, "both", 5, 2));
    }

    private Map<String, Object> availability(long id, long userId, String parity, int dayOfWeek, long timeSlotId) {
        return Map.of("id", id, "userId", userId, "weekParity", parity,
                "dayOfWeek", dayOfWeek, "timeSlotId", timeSlotId, "createTime", "2026-09-01 10:00:00");
    }

    private List<Map<String, Object>> allAvailabilities() {
        return List.of(
                Map.of("userId", 1, "realName", "演示用户A", "username", "demo1", "availabilities", availabilities(1)),
                Map.of("userId", 2, "realName", "演示用户B", "username", "demo2", "availabilities", availabilities(2)),
                Map.of("userId", 3, "realName", "演示用户C", "username", "demo3", "availabilities", availabilities(3)));
    }

    private List<Map<String, Object>> swapRequests() {
        LocalDate monday = week1Monday.plusWeeks(10);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("userId", 2);
        row.put("userName", "演示用户B");
        row.put("type", "dormitory");
        row.put("weekNumber", 11);
        row.put("dutyDate", monday.plusDays(1).toString());
        row.put("timeSlot", "坐班(14:00-17:00)");
        row.put("locationName", "A1栋");
        row.put("targetDay", monday.plusDays(3).toString());
        row.put("targetSlot", "巡班(19:00-21:00)");
        row.put("reason", "当天有课（演示）");
        row.put("status", "pending");
        row.put("createTime", monday.minusDays(2) + " 20:00:00");
        return List.of(row);
    }

    private List<Map<String, Object>> multiDuty() {
        LocalDate monday = week1Monday.plusWeeks(10);
        return List.of(Map.of(
                "id", 1,
                "userId", 1,
                "userName", "演示用户A",
                "type", "dormitory",
                "weekStart", monday.plusWeeks(1).toString(),
                "weekEnd", monday.plusWeeks(1).plusDays(6).toString(),
                "note", "部门迎新需要多排一次（演示）",
                "status", "approved",
                "approverId", 9,
                "createTime", monday.minusDays(4) + " 15:00:00"));
    }

    private List<Map<String, Object>> disciplineRecords(Map<String, String> p) {
        String recordType = p.get("recordType");
        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1, "userId", 2, "userName", "演示用户B",
                        "dutyDate", week1Monday.plusWeeks(8).toString(), "recordType", "late",
                        "note", "迟到 10 分钟（演示）", "createTime", week1Monday.plusWeeks(8).plusDays(1) + " 09:00:00"),
                Map.of("id", 2, "userId", 2, "userName", "演示用户B",
                        "dutyDate", week1Monday.plusWeeks(9).toString(), "recordType", "absent",
                        "note", "未到岗（演示）", "createTime", week1Monday.plusWeeks(9).plusDays(1) + " 09:00:00"));
        return recordType == null ? rows : rows.stream().filter(r -> recordType.equals(r.get("recordType"))).toList();
    }

    private List<Map<String, Object>> disciplineActions(Map<String, String> p) {
        String handled = p.get("handled");
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 1, "userId", 2, "userName", "演示用户B",
                "action", "通报批评", "absentCount", 3, "handled", 0,
                "createTime", "2026-09-15 09:00:00"));
        return handled == null ? rows : rows.stream().filter(r -> handled.equals(String.valueOf(r.get("handled")))).toList();
    }

    private List<Map<String, Object>> messages() {
        return List.of(
                Map.of("id", 1, "title", "国庆假期值班安排", "content", "国庆期间值班按调休表执行，请提前确认调班（演示）",
                        "type", "duty", "senderName", "演示管理员", "status", 1,
                        "createTime", "2026-09-20 10:00:00", "isRead", 0),
                Map.of("id", 2, "title", "九月值班统计已生成", "content", "九月值班统计已生成，可在统计页查看（演示）",
                        "type", "system", "senderName", "演示管理员", "status", 1,
                        "createTime", "2026-09-18 18:00:00", "isRead", 1));
    }

    private List<Map<String, Object>> filterByStatus(List<Map<String, Object>> rows, String status, Long userId) {
        return rows.stream()
                .filter(row -> status == null || status.equals(row.get("status")))
                .filter(row -> userId == null || ((Number) row.get("userId")).longValue() == userId)
                .toList();
    }

    private List<Map<String, Object>> weeks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            LocalDate start = week1Monday.plusWeeks(i);
            rows.add(Map.of("weekNum", i + 1, "startDate", start.toString(), "endDate", start.plusDays(6).toString()));
        }
        return rows;
    }
}
