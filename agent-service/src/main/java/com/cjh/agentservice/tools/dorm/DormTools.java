package com.cjh.agentservice.tools.dorm;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿舍管理系统 Agent 工具集。
 * 设计原则：工具全部只读（仅 GET）、输出裁剪（绝不包含 password、手机号、照片等敏感字段）、
 * 名称解析（楼栋/楼层/房间/学生）由确定性代码在工具内部完成，数据权限由宿舍后端强制。
 * 后端为 RuoYi-Vue：列表接口返回 TableDataInfo(total/rows)，其它接口返回 AjaxResult(data)。
 */
public class DormTools {

    private static final String PAGE_NUM = "1";
    private static final String PAGE_SIZE = "500";
    private static final int DEFAULT_MIN_AVAILABLE_BEDS = 1;
    private static final int DETAIL_SNIPPET_LIMIT = 100;
    private static final int RECENT_RECORD_LIMIT = 5;

    private static final Map<String, String> ROOM_STATUS = Map.of(
            "0", "空闲", "1", "部分占用", "2", "已满", "3", "维修");
    private static final Map<String, String> DORM_STATUS = Map.of(
            "0", "未住宿", "1", "在宿", "2", "已离宿");
    private static final Map<String, String> GENDER = Map.of("0", "男", "1", "女");
    private static final Map<String, String> ENABLE_STATUS = Map.of("0", "正常", "1", "停用");

    private final BusinessClient client;
    private final String token;
    private final ObjectMapper mapper;

    public DormTools(BusinessClient client, String token, ObjectMapper mapper) {
        this.client = client;
        this.token = token;
        this.mapper = mapper;
    }

    // ==================== 总览 ====================

    @Tool(name = "dormOverview", description = "宿舍管理总览：楼栋/楼层/房间/床位数量、在住学生数、空余床位、入住率、空闲/维修房间数、男女生比例。"
            + "宿舍系统没有学期配置，本工具同时返回今天日期；涉及‘现在总体情况’的问题优先调用本工具。")
    public String dormOverview() {
        JsonNode node = data("/dashboard/data", Map.of());
        JsonNode gender = node.path("genderRatio");
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("today", LocalDate.now().toString());
        overview.put("buildingCount", node.path("buildingCount").asLong());
        overview.put("floorCount", node.path("floorCount").asLong());
        overview.put("roomCount", node.path("roomCount").asLong());
        overview.put("totalBedCount", node.path("totalBedCount").asLong());
        overview.put("studentCount", node.path("studentCount").asLong());
        overview.put("occupiedStudentCount", node.path("occupiedStudentCount").asLong());
        overview.put("freeBedCount", node.path("freeBedCount").asLong());
        overview.put("occupancyRate", node.path("occupancyRate").asDouble());
        overview.put("freeRoomCount", node.path("freeRoomCount").asLong());
        overview.put("repairRoomCount", node.path("repairRoomCount").asLong());
        Map<String, Object> ratio = new LinkedHashMap<>();
        ratio.put("maleCount", gender.path("maleCount").asLong());
        ratio.put("femaleCount", gender.path("femaleCount").asLong());
        ratio.put("malePercent", gender.path("malePercent").asDouble());
        ratio.put("femalePercent", gender.path("femalePercent").asDouble());
        overview.put("genderRatio", ratio);
        return json(result("GET /dashboard/data", overview));
    }

    // ==================== 床位 / 房间 ====================

    @Tool(name = "availableBeds", description = "查询空闲床位：可按楼栋名称、楼层（如 2楼 或 2，需同时提供楼栋名）、房间号过滤，"
            + "返回有空床且非维修状态的房间（楼栋/楼层/房间号/床位总数/剩余床位/状态）。楼栋支持全名、别名或部分匹配。")
    public String availableBeds(
            @ToolParam(required = false, description = "楼栋名称，如 演示1号楼") String buildingName,
            @ToolParam(required = false, description = "楼层，如 2楼 或 2；需同时提供楼栋名称") String floorKeyword,
            @ToolParam(required = false, description = "房间号，如 101") String roomNumber,
            @ToolParam(required = false, description = "最少剩余床位数，默认 1") Integer minAvailableBeds) {
        Map<String, String> params = pageParams(new LinkedHashMap<>());
        String resolvedBuilding = "";
        String resolvedFloor = "";
        if (notBlank(buildingName)) {
            JsonNode building = resolveBuilding(buildingName);
            long buildingId = building.path("buildingId").asLong();
            params.put("buildingId", String.valueOf(buildingId));
            resolvedBuilding = building.path("buildingName").asText("");
            if (notBlank(floorKeyword)) {
                JsonNode floor = resolveFloor(buildingId, floorKeyword);
                params.put("floorId", floor.path("floorId").asText());
                resolvedFloor = floor.path("floorName").asText("");
            }
        } else if (notBlank(floorKeyword)) {
            throw new BusinessApiException(400, "按楼层筛选时请同时提供楼栋名称");
        }
        if (notBlank(roomNumber)) {
            params.put("roomNumber", roomNumber.trim());
        }
        int minBeds = minAvailableBeds == null || minAvailableBeds < 1 ? DEFAULT_MIN_AVAILABLE_BEDS : minAvailableBeds;
        params.put("minAvailableBeds", String.valueOf(minBeds));
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (JsonNode room : rows(data("/room/room/list", params))) {
            if (!"3".equals(room.path("status").asText(""))) {
                rooms.add(compactRoom(room));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("building", resolvedBuilding);
        payload.put("floor", resolvedFloor);
        payload.put("minAvailableBeds", minBeds);
        payload.put("roomCount", rooms.size());
        payload.put("rooms", rooms);
        return json(result("GET /room/room/list?minAvailableBeds=" + minBeds, payload));
    }

    @Tool(name = "roomOccupancy", description = "查询某楼栋某房间的占用情况：床位总数、剩余床位、房间状态、"
            + "在住学生（姓名/学号/班级/床位）与空闲床位号。")
    public String roomOccupancy(
            @ToolParam(description = "楼栋名称，如 演示1号楼") String buildingName,
            @ToolParam(description = "房间号，如 101") String roomNumber) {
        JsonNode building = resolveBuilding(buildingName);
        JsonNode room = resolveRoom(building, roomNumber);
        long roomId = room.path("roomId").asLong();
        List<Map<String, Object>> occupants = new ArrayList<>();
        for (JsonNode student : rows(data("/student_info/studentInfo/byRoom/" + roomId, Map.of()))) {
            occupants.add(compactOccupant(student));
        }
        List<String> bedNumbers = new ArrayList<>();
        JsonNode availableBeds = data("/room/room/" + roomId + "/availableBeds", Map.of());
        if (availableBeds.isArray()) {
            availableBeds.forEach(bed -> bedNumbers.add(bed.asText("")));
        }
        Map<String, Object> payload = compactRoom(room);
        payload.put("occupants", occupants);
        payload.put("availableBedNumbers", bedNumbers);
        return json(result("GET /student_info/studentInfo/byRoom/" + roomId
                + " + GET /room/room/" + roomId + "/availableBeds", payload));
    }

    // ==================== 学生 ====================

    @Tool(name = "studentDorm", description = "查询某个学生的住宿信息（楼栋/楼层/房间号/床位/在宿状态）。参数可传姓名或学号；"
            + "重名时要求更精确的输入。未分配宿舍的学生会显示“未住宿”。")
    public String studentDorm(@ToolParam(description = "学生姓名或学号") String studentKeyword) {
        JsonNode student = resolveStudent(studentKeyword);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", studentKeyword);
        payload.putAll(compactStudent(student));
        return json(result("GET /student_info/studentInfo/list", payload));
    }

    @Tool(name = "userLookup", description = "按姓名或学号查询学生（支持模糊）：返回 studentId、学号、姓名、性别、学院、班级、辅导员、"
            + "楼栋/房间/床位与在宿状态。用于获取 studentId 或确认学生身份；输出不含手机号等敏感信息。")
    public String userLookup(@ToolParam(description = "学生姓名或学号关键词，如 演示") String keyword) {
        if (!notBlank(keyword)) {
            throw new BusinessApiException(400, "请提供学生姓名或学号关键词");
        }
        String trimmed = keyword.trim();
        Map<String, String> params = pageParams(new LinkedHashMap<>());
        params.put(trimmed.matches("\\d+") ? "studentCode" : "studentName", trimmed);
        List<Map<String, Object>> students = new ArrayList<>();
        for (JsonNode student : rows(data("/student_info/studentInfo/list", params))) {
            students.add(compactStudent(student));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", keyword);
        payload.put("count", students.size());
        payload.put("students", students);
        return json(result("GET /student_info/studentInfo/list", payload));
    }

    // ==================== 辅导员 / 违规 ====================

    @Tool(name = "counselorScope", description = "查询辅导员的管辖范围：每位辅导员在各楼栋的在住学生数。可按辅导员姓名过滤；"
            + "不传则返回全部辅导员。仅具备房间查看权限的账号可用。")
    public String counselorScope(@ToolParam(required = false, description = "辅导员姓名，可不传") String counselorKeyword) {
        LinkedHashMap<Long, List<JsonNode>> grouped = new LinkedHashMap<>();
        for (JsonNode row : rows(data("/dashboard/counselorBuildingStudentCount", Map.of()))) {
            long counselorId = row.path("counselorId").asLong();
            grouped.computeIfAbsent(counselorId, key -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> counselors = new ArrayList<>();
        for (List<JsonNode> items : grouped.values()) {
            List<Map<String, Object>> buildings = new ArrayList<>();
            long totalStudents = 0;
            for (JsonNode item : items) {
                long count = item.path("studentCount").asLong();
                Map<String, Object> building = new LinkedHashMap<>();
                building.put("buildingName", item.path("buildingName").asText(""));
                building.put("studentCount", count);
                buildings.add(building);
                totalStudents += count;
            }
            Map<String, Object> counselor = new LinkedHashMap<>();
            counselor.put("counselorId", items.get(0).path("counselorId").asLong());
            counselor.put("counselorName", items.get(0).path("counselorName").asText(""));
            counselor.put("buildings", buildings);
            counselor.put("totalStudents", totalStudents);
            counselors.add(counselor);
        }
        List<Map<String, Object>> matched = filterCounselors(counselors, counselorKeyword);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("counselorCount", matched.size());
        payload.put("counselors", matched);
        return json(result("GET /dashboard/counselorBuildingStudentCount", payload));
    }

    @Tool(name = "violationStats", description = "宿舍违规记录与统计：可按学生（姓名/学号）、楼栋、违规类型过滤，"
            + "返回总数、按类型/状态/楼栋聚合与最近记录。处理状态为系统字典值（0/1）。")
    public String violationStats(
            @ToolParam(required = false, description = "学生姓名或学号") String studentKeyword,
            @ToolParam(required = false, description = "楼栋名称") String buildingName,
            @ToolParam(required = false, description = "违规类型（系统字典值，如 晚归）") String violationType) {
        Map<String, String> params = pageParams(new LinkedHashMap<>());
        if (notBlank(studentKeyword)) {
            String keyword = studentKeyword.trim();
            params.put(keyword.matches("\\d+") ? "studentCode" : "studentName", keyword);
        }
        if (notBlank(buildingName)) {
            params.put("buildingName", resolveBuilding(buildingName).path("buildingName").asText(""));
        }
        if (notBlank(violationType)) {
            params.put("violationType", violationType.trim());
        }
        List<JsonNode> records = rows(data("/record/record/list", params));
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byBuilding = new LinkedHashMap<>();
        List<Map<String, Object>> recent = new ArrayList<>();
        for (JsonNode record : records) {
            count(byType, record.path("violationType").asText("未知类型"));
            count(byStatus, record.path("status").asText("未知状态"));
            count(byBuilding, record.path("buildingName").asText("未知楼栋"));
            if (recent.size() < RECENT_RECORD_LIMIT) {
                recent.add(compactViolation(record));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", records.size());
        payload.put("byType", byType);
        payload.put("byStatus", byStatus);
        payload.put("byBuilding", byBuilding);
        payload.put("recent", recent);
        return json(result("GET /record/record/list", payload));
    }

    // ==================== 楼栋基础信息 ====================

    @Tool(name = "buildingList", description = "楼栋/楼层/房间基础信息列表：返回楼栋（含别名/校区/状态）、楼层及其房间"
            + "（房间号/床位总数/剩余床位/状态）。可按楼栋名称过滤；includeRooms=false 时只返回楼层汇总。")
    public String buildingList(
            @ToolParam(required = false, description = "楼栋名称（全名/别名/部分匹配），可不传") String buildingName,
            @ToolParam(required = false, description = "是否包含房间明细，默认 true") Boolean includeRooms) {
        List<JsonNode> buildings = notBlank(buildingName)
                ? List.of(resolveBuilding(buildingName))
                : rows(data("/building/building/listSelect", Map.of()));
        boolean withRooms = includeRooms == null || includeRooms;
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode building : buildings) {
            items.add(compactBuilding(building, withRooms));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingCount", items.size());
        payload.put("buildings", items);
        return json(result("GET /building/building/listSelect + GET /floor/floor/listByBuildingId + GET /room/room/list", payload));
    }

    // ==================== 内部实现 ====================

    private JsonNode data(String path, Map<String, String> params) {
        return client.get(token, path, params);
    }

    private JsonNode resolveBuilding(String buildingName) {
        if (!notBlank(buildingName)) {
            throw new BusinessApiException(400, "请提供楼栋名称，如 演示1号楼");
        }
        List<JsonNode> buildings = rows(data("/building/building/listSelect", Map.of()));
        String input = normalize(buildingName);
        List<JsonNode> exact = new ArrayList<>();
        List<JsonNode> fuzzy = new ArrayList<>();
        for (JsonNode building : buildings) {
            String name = normalize(building.path("buildingName").asText(""));
            String alias = normalize(building.path("buildingAlias").asText(""));
            if (name.equals(input) || (!alias.isEmpty() && alias.equals(input))) {
                exact.add(building);
            } else if (!input.isEmpty() && (!name.isEmpty() && (name.contains(input) || input.contains(name))
                    || !alias.isEmpty() && (alias.contains(input) || input.contains(alias)))) {
                fuzzy.add(building);
            }
        }
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw ambiguity("楼栋", exact, "buildingName");
        }
        if (fuzzy.size() == 1) {
            return fuzzy.get(0);
        }
        if (fuzzy.size() > 1) {
            throw ambiguity("楼栋", fuzzy, "buildingName");
        }
        throw new BusinessApiException(404, "没有找到楼栋：" + buildingName + "，可先调用 buildingList 查询楼栋列表");
    }

    private JsonNode resolveFloor(long buildingId, String floorKeyword) {
        if (!notBlank(floorKeyword)) {
            throw new BusinessApiException(400, "请提供楼层，如 2楼 或 2");
        }
        List<JsonNode> floors = rows(data("/floor/floor/listByBuildingId", Map.of("buildingId", String.valueOf(buildingId))));
        String input = normalizeFloor(floorKeyword);
        List<JsonNode> exact = new ArrayList<>();
        List<JsonNode> fuzzy = new ArrayList<>();
        for (JsonNode floor : floors) {
            String name = normalizeFloor(floor.path("floorName").asText(""));
            String number = floor.path("floorNumber").asText("").trim();
            if (name.equals(input) || number.equals(input)) {
                exact.add(floor);
            } else if (!name.isEmpty() && (name.contains(input) || input.contains(name))) {
                fuzzy.add(floor);
            }
        }
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw ambiguity("楼层", exact, "floorName");
        }
        if (fuzzy.size() == 1) {
            return fuzzy.get(0);
        }
        if (fuzzy.size() > 1) {
            throw ambiguity("楼层", fuzzy, "floorName");
        }
        throw new BusinessApiException(404, "该楼栋没有找到楼层：" + floorKeyword);
    }

    private JsonNode resolveRoom(JsonNode building, String roomNumber) {
        if (!notBlank(roomNumber)) {
            throw new BusinessApiException(400, "请提供房间号");
        }
        String trimmed = roomNumber.trim();
        Map<String, String> params = pageParams(new LinkedHashMap<>());
        params.put("buildingId", building.path("buildingId").asText());
        params.put("roomNumber", trimmed);
        List<JsonNode> rooms = rows(data("/room/room/list", params));
        if (rooms.isEmpty()) {
            throw new BusinessApiException(404,
                    "在楼栋 " + building.path("buildingName").asText("") + " 中没有找到房间：" + trimmed);
        }
        if (rooms.size() > 1) {
            throw ambiguity("房间", rooms, "roomNumber");
        }
        return rooms.get(0);
    }

    private JsonNode resolveStudent(String studentKeyword) {
        if (!notBlank(studentKeyword)) {
            throw new BusinessApiException(400, "请提供学生姓名或学号");
        }
        String trimmed = studentKeyword.trim();
        boolean byCode = trimmed.matches("\\d+");
        Map<String, String> params = pageParams(new LinkedHashMap<>());
        params.put(byCode ? "studentCode" : "studentName", trimmed);
        List<JsonNode> candidates = rows(data("/student_info/studentInfo/list", params));
        List<JsonNode> exact = new ArrayList<>();
        List<JsonNode> fuzzy = new ArrayList<>();
        for (JsonNode candidate : candidates) {
            String name = candidate.path("studentName").asText("");
            String code = candidate.path("studentCode").asText("");
            if (byCode ? code.equals(trimmed) : name.equals(trimmed)) {
                exact.add(candidate);
            } else if (!byCode && (name.contains(trimmed) || code.contains(trimmed))) {
                fuzzy.add(candidate);
            }
        }
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw studentAmbiguity(exact);
        }
        if (fuzzy.size() == 1) {
            return fuzzy.get(0);
        }
        if (fuzzy.size() > 1) {
            throw studentAmbiguity(fuzzy);
        }
        throw new BusinessApiException(404, "没有找到学生：" + studentKeyword + "，请确认姓名或学号");
    }

    private List<Map<String, Object>> filterCounselors(List<Map<String, Object>> counselors, String keyword) {
        if (!notBlank(keyword)) {
            return counselors;
        }
        String input = keyword.trim();
        List<Map<String, Object>> exact = counselors.stream()
                .filter(counselor -> input.equals(String.valueOf(counselor.get("counselorName"))))
                .toList();
        if (exact.size() == 1) {
            return exact;
        }
        List<Map<String, Object>> fuzzy = counselors.stream()
                .filter(counselor -> String.valueOf(counselor.get("counselorName")).contains(input))
                .toList();
        if (fuzzy.size() == 1) {
            return fuzzy;
        }
        if (fuzzy.isEmpty()) {
            throw new BusinessApiException(404, "没有找到辅导员：" + keyword);
        }
        StringBuilder names = new StringBuilder();
        for (Map<String, Object> counselor : fuzzy) {
            if (!names.isEmpty()) {
                names.append("、");
            }
            names.append(counselor.get("counselorName"));
        }
        throw new BusinessApiException(400, "匹配到多个辅导员：" + names + "，请使用完整姓名");
    }

    private List<JsonNode> rows(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            node.forEach(result::add);
            return result;
        }
        JsonNode rows = node.path("rows");
        if (rows.isArray()) {
            rows.forEach(result::add);
            return result;
        }
        JsonNode data = node.path("data");
        if (data.isArray()) {
            data.forEach(result::add);
        } else if (data.path("rows").isArray()) {
            data.path("rows").forEach(result::add);
        }
        return result;
    }

    private Map<String, Object> compactRoom(JsonNode room) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("roomId", room.path("roomId").asLong());
        item.put("buildingName", room.path("buildingName").asText(""));
        item.put("floorName", room.path("floorName").asText(""));
        item.put("roomNumber", room.path("roomNumber").asText(""));
        item.put("bedCount", room.path("bedCount").asLong());
        item.put("availableBeds", room.path("availableBeds").asLong());
        item.put("status", ROOM_STATUS.getOrDefault(room.path("status").asText(""), room.path("status").asText("")));
        return item;
    }

    private Map<String, Object> compactStudent(JsonNode student) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studentId", student.path("studentId").asLong());
        item.put("studentCode", student.path("studentCode").asText(""));
        item.put("studentName", student.path("studentName").asText(""));
        item.put("gender", GENDER.getOrDefault(student.path("gender").asText(""), student.path("gender").asText("")));
        item.put("collegeName", student.path("collegeName").asText(""));
        item.put("className", student.path("className").asText(""));
        item.put("counselorName", student.path("counselorName").asText(""));
        item.put("buildingName", student.path("buildingName").asText(""));
        item.put("floorName", student.path("floorName").asText(""));
        item.put("roomNumber", student.path("roomNumber").asText(""));
        item.put("bedNumber", student.path("bedNumber").asText(""));
        item.put("dormStatus", DORM_STATUS.getOrDefault(student.path("dormStatus").asText(""),
                student.path("dormStatus").asText("")));
        return item;
    }

    private Map<String, Object> compactOccupant(JsonNode student) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studentId", student.path("studentId").asLong());
        item.put("studentCode", student.path("studentCode").asText(""));
        item.put("studentName", student.path("studentName").asText(""));
        item.put("className", student.path("className").asText(""));
        item.put("bedNumber", student.path("bedNumber").asText(""));
        item.put("dormStatus", DORM_STATUS.getOrDefault(student.path("dormStatus").asText(""),
                student.path("dormStatus").asText("")));
        return item;
    }

    private Map<String, Object> compactViolation(JsonNode record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("recordId", record.path("recordId").asLong());
        item.put("studentCode", record.path("studentCode").asText(""));
        item.put("studentName", record.path("studentName").asText(""));
        item.put("buildingName", record.path("buildingName").asText(""));
        item.put("floorName", record.path("floorName").asText(""));
        item.put("roomNumber", record.path("roomNumber").asText(""));
        item.put("violationType", record.path("violationType").asText(""));
        String detail = record.path("violationDetail").asText("");
        item.put("violationDetail", detail.length() > DETAIL_SNIPPET_LIMIT
                ? detail.substring(0, DETAIL_SNIPPET_LIMIT) + "…" : detail);
        item.put("status", record.path("status").asText(""));
        item.put("createTime", record.path("createTime").asText(""));
        return item;
    }

    private Map<String, Object> compactBuilding(JsonNode building, boolean withRooms) {
        long buildingId = building.path("buildingId").asLong();
        List<JsonNode> floors = rows(data("/floor/floor/listByBuildingId", Map.of("buildingId", String.valueOf(buildingId))));
        List<JsonNode> rooms = withRooms
                ? rows(data("/room/room/list", pageParams(Map.of("buildingId", String.valueOf(buildingId)))))
                : List.of();
        Map<Long, List<JsonNode>> roomsByFloor = new LinkedHashMap<>();
        for (JsonNode room : rooms) {
            roomsByFloor.computeIfAbsent(room.path("floorId").asLong(), key -> new ArrayList<>()).add(room);
        }
        List<Map<String, Object>> floorItems = new ArrayList<>();
        long totalBeds = 0;
        long totalAvailable = 0;
        for (JsonNode floor : floors) {
            long floorId = floor.path("floorId").asLong();
            List<JsonNode> floorRooms = roomsByFloor.getOrDefault(floorId, List.of());
            List<Map<String, Object>> roomItems = new ArrayList<>();
            long bedCount = 0;
            long availableBeds = 0;
            for (JsonNode room : floorRooms) {
                roomItems.add(compactRoom(room));
                bedCount += room.path("bedCount").asLong();
                availableBeds += room.path("availableBeds").asLong();
            }
            totalBeds += bedCount;
            totalAvailable += availableBeds;
            Map<String, Object> floorItem = new LinkedHashMap<>();
            floorItem.put("floorId", floorId);
            floorItem.put("floorName", floor.path("floorName").asText(""));
            floorItem.put("roomCount", floorRooms.size());
            floorItem.put("bedCount", bedCount);
            floorItem.put("availableBeds", availableBeds);
            if (withRooms) {
                floorItem.put("rooms", roomItems);
            }
            floorItems.add(floorItem);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("buildingId", buildingId);
        item.put("buildingName", building.path("buildingName").asText(""));
        item.put("buildingAlias", building.path("buildingAlias").asText(""));
        item.put("campusCode", building.path("campusCode").asText(""));
        item.put("status", ENABLE_STATUS.getOrDefault(building.path("status").asText(""), building.path("status").asText("")));
        item.put("floorCount", floorItems.size());
        item.put("roomCount", rooms.size());
        item.put("bedCount", totalBeds);
        item.put("availableBeds", totalAvailable);
        item.put("floors", floorItems);
        return item;
    }

    private Map<String, String> pageParams(Map<String, String> params) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("pageNum", PAGE_NUM);
        result.put("pageSize", PAGE_SIZE);
        result.putAll(params);
        return result;
    }

    private BusinessApiException ambiguity(String what, List<JsonNode> matches, String field) {
        StringBuilder names = new StringBuilder();
        for (JsonNode match : matches) {
            if (!names.isEmpty()) {
                names.append("、");
            }
            names.append(match.path(field).asText(""));
        }
        return new BusinessApiException(400, what + "名不唯一，匹配到：" + names + "，请使用完整名称");
    }

    private BusinessApiException studentAmbiguity(List<JsonNode> matches) {
        StringBuilder names = new StringBuilder();
        for (JsonNode match : matches) {
            if (!names.isEmpty()) {
                names.append("、");
            }
            names.append(match.path("studentName").asText(""))
                    .append("(").append(match.path("studentCode").asText("")).append(")");
        }
        return new BusinessApiException(400, "匹配到多个学生：" + names + "，请使用学号精确查询");
    }

    private void count(Map<String, Long> counter, String key) {
        counter.merge(key, 1L, Long::sum);
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

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private String normalizeFloor(String value) {
        return normalize(value).replace("楼", "").replace("层", "");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
