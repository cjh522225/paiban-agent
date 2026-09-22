package com.cjh.agentservice.demo;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿舍管理演示模式数据源：合成数据，不依赖任何外部系统，不使用真实学校/学生信息。
 * 与真实版共用同一套 DormTools @Tool，保证 dorm 演示可独立运行。
 * 为贴近 RuoYi 真实响应：列表接口返回 {total,rows,code,msg}，其它接口返回数据本体。
 */
public class DemoDormClient implements BusinessClient {

    private static final long COLLEGE_ID = 1L;

    private record BuildingDef(long buildingId, String buildingName, String buildingAlias) {
    }

    private record FloorDef(long floorId, long buildingId, String floorName, long floorNumber) {
    }

    private record RoomDef(long roomId, long floorId, long buildingId, long roomNumber,
                           long bedCount, String forcedStatus) {
    }

    private record CounselorDef(long counselorId, long userId, String counselorName) {
    }

    private static final List<BuildingDef> BUILDINGS = List.of(
            new BuildingDef(1, "演示1号楼", "演示东区"),
            new BuildingDef(2, "演示2号楼", "演示西区"),
            new BuildingDef(3, "演示3号楼", "演示南区"));

    private static final List<FloorDef> FLOORS = List.of(
            new FloorDef(11, 1, "1楼", 1),
            new FloorDef(12, 1, "2楼", 2),
            new FloorDef(21, 2, "1楼", 1),
            new FloorDef(31, 3, "1楼", 1));

    private static final List<RoomDef> ROOMS = List.of(
            new RoomDef(101, 11, 1, 101, 4, null),
            new RoomDef(102, 11, 1, 102, 2, null),
            new RoomDef(201, 12, 1, 201, 2, "3"),
            new RoomDef(211, 21, 2, 101, 2, null),
            new RoomDef(301, 31, 3, 101, 2, null));

    private static final List<CounselorDef> COUNSELORS = List.of(
            new CounselorDef(1, 101, "演示辅导员A"),
            new CounselorDef(2, 102, "演示辅导员B"));

    private final ObjectMapper mapper;

    public DemoDormClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JsonNode get(String token, String path, Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;

        if (path.equals("/dashboard/data")) {
            return tree(dashboard());
        }
        if (path.equals("/dashboard/counselorBuildingStudentCount")) {
            return tree(counselorBuildingStudentCount());
        }
        if (path.equals("/building/building/listSelect")) {
            return table(filterBuildings(p));
        }
        if (path.equals("/floor/floor/listByBuildingId")) {
            return table(floorsByBuilding(p));
        }
        if (path.equals("/room/room/list")) {
            return table(filterRooms(p));
        }
        if (path.startsWith("/room/room/") && path.endsWith("/availableBeds")) {
            long roomId = Long.parseLong(path.substring("/room/room/".length(), path.length() - "/availableBeds".length()));
            return tree(availableBedNumbers(roomId));
        }
        if (path.equals("/student_info/studentInfo/list")) {
            return table(filterStudents(p));
        }
        if (path.startsWith("/student_info/studentInfo/byRoom/")) {
            long roomId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            return tree(studentsByRoom(roomId));
        }
        if (path.equals("/counselor/counselor/list")) {
            return table(filterCounselors(p));
        }
        if (path.equals("/record/record/list")) {
            return table(filterViolationRecords(p));
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

    private JsonNode table(List<Map<String, Object>> rows) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("total", rows.size());
        page.put("rows", rows);
        page.put("code", 200);
        page.put("msg", "查询成功");
        return tree(page);
    }

    // ==================== 基础数据 ====================

    private List<Map<String, Object>> buildings() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BuildingDef def : BUILDINGS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("buildingId", def.buildingId());
            row.put("campusCode", "DEMO");
            row.put("buildingName", def.buildingName());
            row.put("buildingAlias", def.buildingAlias());
            row.put("sortOrder", def.buildingId());
            row.put("status", "0");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> floors() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FloorDef def : FLOORS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("floorId", def.floorId());
            row.put("buildingId", def.buildingId());
            row.put("buildingName", buildingName(def.buildingId()));
            row.put("floorNumber", def.floorNumber());
            row.put("floorName", def.floorName());
            row.put("sortOrder", def.floorNumber());
            row.put("status", "0");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> rooms() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RoomDef def : ROOMS) {
            long occupied = occupiedCount(def.roomId());
            long available = def.bedCount() - occupied;
            String status = def.forcedStatus() != null ? def.forcedStatus()
                    : occupied == 0 ? "0" : occupied >= def.bedCount() ? "2" : "1";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId", def.roomId());
            row.put("floorId", def.floorId());
            row.put("floorName", floorName(def.floorId()));
            row.put("buildingId", def.buildingId());
            row.put("buildingName", buildingName(def.buildingId()));
            row.put("roomNumber", def.roomNumber());
            row.put("roomType", "0");
            row.put("bedCount", def.bedCount());
            row.put("availableBeds", available);
            row.put("status", status);
            row.put("sortOrder", def.roomNumber());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> students() {
        return List.of(
                student(1, "20260001", "演示学生甲", "0", 1, "演示计算机1班", 1, "演示辅导员A", 101L, "1", "1"),
                student(2, "20260002", "演示学生乙", "0", 1, "演示计算机1班", 1, "演示辅导员A", 101L, "2", "1"),
                student(3, "20260003", "演示学生丙", "0", 2, "演示软件1班", 1, "演示辅导员A", 101L, "3", "1"),
                student(4, "20260004", "演示学生丁", "1", 2, "演示软件1班", 2, "演示辅导员B", 211L, "1", "1"),
                student(5, "20260005", "演示学生戊", "1", 3, "演示网络1班", 2, "演示辅导员B", 211L, "2", "1"),
                student(6, "20260006", "演示学生己", "0", 3, "演示网络1班", 1, "演示辅导员A", 102L, "1", "1"),
                student(7, "20260007", "演示学生庚", "0", 1, "演示计算机1班", 1, "演示辅导员A", null, null, "0"));
    }

    private Map<String, Object> student(long studentId, String studentCode, String studentName, String gender,
                                        long classId, String className, long counselorId, String counselorName,
                                        Long roomId, String bedNumber, String dormStatus) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentId", studentId);
        row.put("studentCode", studentCode);
        row.put("studentName", studentName);
        row.put("gender", gender);
        row.put("collegeId", COLLEGE_ID);
        row.put("collegeName", "演示学院");
        row.put("classId", classId);
        row.put("className", className);
        row.put("counselorId", counselorId);
        row.put("counselorName", counselorName);
        row.put("grade", "演示2026级");
        row.put("studentType", "0");
        row.put("roomId", roomId);
        row.put("bedNumber", bedNumber);
        row.put("bedNumberChinese", bedNumber == null ? null : "第" + bedNumber + "床");
        row.put("phone", "1380000000" + studentId);
        row.put("photoUrl", "/demo/photo/student" + studentId + ".jpg");
        row.put("dormStatus", dormStatus);
        row.put("status", "0");
        if (roomId != null) {
            RoomDef room = roomById(roomId);
            row.put("floorIdForRoom", room.floorId());
            row.put("buildingIdForRoom", room.buildingId());
            row.put("buildingName", buildingName(room.buildingId()));
            row.put("floorName", floorName(room.floorId()));
            row.put("roomNumber", String.valueOf(room.roomNumber()));
        } else {
            row.put("buildingName", null);
            row.put("floorName", null);
            row.put("roomNumber", null);
        }
        return row;
    }

    private List<Map<String, Object>> counselors() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CounselorDef def : COUNSELORS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("counselorId", def.counselorId());
            row.put("userId", def.userId());
            row.put("counselorName", def.counselorName());
            row.put("collegeId", COLLEGE_ID);
            row.put("collegeName", "演示学院");
            // 敏感字段仅用于演示与测试：工具输出必须裁剪，绝不透出
            row.put("phone", "1390000000" + def.counselorId());
            row.put("password", "demo-password");
            row.put("delFlag", "0");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> violationRecords() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(violation(1, 2, "20260002", "演示学生乙", 101L, 101L, "晚归", "演示数据：22:40 晚归登记", "0", "2026-09-15 22:40:00"));
        rows.add(violation(2, 2, "20260002", "演示学生乙", 101L, 101L, "使用违规电器", "演示数据：宿舍内使用违规电器", "1", "2026-09-10 20:00:00"));
        rows.add(violation(3, 4, "20260004", "演示学生丁", 211L, 101L, "晚归", "演示数据：23:05 晚归登记", "0", "2026-09-18 23:05:00"));
        return rows;
    }

    private Map<String, Object> violation(long recordId, long studentId, String studentCode, String studentName,
                                          long roomId, long roomNumber, String violationType, String detail,
                                          String status, String createTime) {
        RoomDef room = roomById(roomId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordId", recordId);
        row.put("studentId", studentId);
        row.put("roomId", roomId);
        row.put("studentCode", studentCode);
        row.put("studentName", studentName);
        row.put("buildingName", buildingName(room.buildingId()));
        row.put("floorName", floorName(room.floorId()));
        row.put("roomNumber", String.valueOf(roomNumber));
        row.put("violationType", violationType);
        row.put("violationDetail", detail);
        row.put("status", status);
        row.put("createTime", createTime);
        return row;
    }

    // ==================== 统计 ====================

    private Map<String, Object> dashboard() {
        long totalBeds = ROOMS.stream().mapToLong(RoomDef::bedCount).sum();
        long occupied = students().stream().filter(s -> "1".equals(s.get("dormStatus"))).count();
        long freeBeds = totalBeds - occupied;
        long male = students().stream().filter(s -> "0".equals(s.get("gender"))).count();
        long female = students().stream().filter(s -> "1".equals(s.get("gender"))).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("buildingCount", (long) BUILDINGS.size());
        data.put("floorCount", (long) FLOORS.size());
        data.put("roomCount", (long) ROOMS.size());
        data.put("totalBedCount", totalBeds);
        data.put("studentCount", (long) students().size());
        data.put("occupiedStudentCount", occupied);
        data.put("freeBedCount", freeBeds);
        data.put("occupancyRate", percent(occupied, totalBeds));
        data.put("freeRoomCount", rooms().stream().filter(r -> "0".equals(r.get("status"))).count());
        data.put("repairRoomCount", rooms().stream().filter(r -> "3".equals(r.get("status"))).count());
        Map<String, Object> genderRatio = new LinkedHashMap<>();
        genderRatio.put("maleCount", male);
        genderRatio.put("femaleCount", female);
        genderRatio.put("malePercent", percent(male, male + female));
        genderRatio.put("femalePercent", percent(female, male + female));
        data.put("genderRatio", genderRatio);
        return data;
    }

    private List<Map<String, Object>> counselorBuildingStudentCount() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CounselorDef counselor : COUNSELORS) {
            for (BuildingDef building : BUILDINGS) {
                long count = students().stream()
                        .filter(s -> "1".equals(s.get("dormStatus")) && s.get("roomId") != null)
                        .filter(s -> roomById(((Number) s.get("roomId")).longValue()).buildingId() == building.buildingId())
                        .filter(s -> ((Number) s.get("counselorId")).longValue() == counselor.counselorId())
                        .count();
                if (count > 0) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("counselorId", counselor.counselorId());
                    row.put("counselorName", counselor.counselorName());
                    row.put("buildingId", building.buildingId());
                    row.put("buildingName", building.buildingName());
                    row.put("studentCount", count);
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    // ==================== 查询过滤 ====================

    private List<Map<String, Object>> filterBuildings(Map<String, String> p) {
        String name = p.get("buildingName");
        String campusCode = p.get("campusCode");
        String status = p.get("status");
        return buildings().stream()
                .filter(row -> name == null || String.valueOf(row.get("buildingName")).contains(name))
                .filter(row -> campusCode == null || campusCode.equals(row.get("campusCode")))
                .filter(row -> status == null || status.equals(row.get("status")))
                .toList();
    }

    private List<Map<String, Object>> floorsByBuilding(Map<String, String> p) {
        String buildingId = p.get("buildingId");
        if (buildingId == null) {
            return List.of();
        }
        return floors().stream()
                .filter(row -> buildingId.equals(String.valueOf(row.get("buildingId"))))
                .filter(row -> "0".equals(row.get("status")))
                .toList();
    }

    private List<Map<String, Object>> filterRooms(Map<String, String> p) {
        String buildingId = p.get("buildingId");
        String floorId = p.get("floorId");
        String roomNumber = p.get("roomNumber");
        String status = p.get("status");
        String minAvailableBeds = p.get("minAvailableBeds");
        return rooms().stream()
                .filter(row -> buildingId == null || buildingId.equals(String.valueOf(row.get("buildingId"))))
                .filter(row -> floorId == null || floorId.equals(String.valueOf(row.get("floorId"))))
                .filter(row -> roomNumber == null || roomNumber.equals(String.valueOf(row.get("roomNumber"))))
                .filter(row -> status == null || status.equals(row.get("status")))
                .filter(row -> minAvailableBeds == null
                        || ((Number) row.get("availableBeds")).longValue() >= Long.parseLong(minAvailableBeds))
                .toList();
    }

    private List<Map<String, Object>> filterStudents(Map<String, String> p) {
        String studentCode = p.get("studentCode");
        String studentName = p.get("studentName");
        String gender = p.get("gender");
        String roomId = p.get("roomId");
        String buildingId = p.get("buildingId");
        String floorId = p.get("floorId");
        String dormStatus = p.get("dormStatus");
        String counselorName = p.get("counselorName");
        return students().stream()
                .filter(row -> studentCode == null || String.valueOf(row.get("studentCode")).contains(studentCode))
                .filter(row -> studentName == null || String.valueOf(row.get("studentName")).contains(studentName))
                .filter(row -> gender == null || gender.equals(row.get("gender")))
                .filter(row -> roomId == null || (row.get("roomId") != null
                        && roomId.equals(String.valueOf(row.get("roomId")))))
                .filter(row -> buildingId == null || (row.get("buildingIdForRoom") != null
                        && buildingId.equals(String.valueOf(row.get("buildingIdForRoom")))))
                .filter(row -> floorId == null || (row.get("floorIdForRoom") != null
                        && floorId.equals(String.valueOf(row.get("floorIdForRoom")))))
                .filter(row -> dormStatus == null || dormStatus.equals(row.get("dormStatus")))
                .filter(row -> counselorName == null || String.valueOf(row.get("counselorName")).contains(counselorName))
                .toList();
    }

    private List<Map<String, Object>> filterCounselors(Map<String, String> p) {
        String counselorName = p.get("counselorName");
        return counselors().stream()
                .filter(row -> counselorName == null || String.valueOf(row.get("counselorName")).contains(counselorName))
                .toList();
    }

    private List<Map<String, Object>> filterViolationRecords(Map<String, String> p) {
        String studentCode = p.get("studentCode");
        String studentName = p.get("studentName");
        String buildingName = p.get("buildingName");
        String violationType = p.get("violationType");
        String status = p.get("status");
        return violationRecords().stream()
                .filter(row -> studentCode == null || String.valueOf(row.get("studentCode")).contains(studentCode))
                .filter(row -> studentName == null || String.valueOf(row.get("studentName")).contains(studentName))
                .filter(row -> buildingName == null || String.valueOf(row.get("buildingName")).contains(buildingName))
                .filter(row -> violationType == null || violationType.equals(row.get("violationType")))
                .filter(row -> status == null || status.equals(row.get("status")))
                .sorted((a, b) -> String.valueOf(b.get("createTime")).compareTo(String.valueOf(a.get("createTime"))))
                .toList();
    }

    private List<String> availableBedNumbers(long roomId) {
        RoomDef room = roomById(roomId);
        List<String> occupied = studentsByRoom(roomId).stream()
                .map(row -> String.valueOf(row.get("bedNumber")))
                .toList();
        List<String> available = new ArrayList<>();
        for (long bed = 1; bed <= room.bedCount(); bed++) {
            String bedNumber = String.valueOf(bed);
            if (!occupied.contains(bedNumber)) {
                available.add(bedNumber);
            }
        }
        return available;
    }

    private List<Map<String, Object>> studentsByRoom(long roomId) {
        return students().stream()
                .filter(row -> "1".equals(row.get("dormStatus")) && row.get("roomId") != null
                        && ((Number) row.get("roomId")).longValue() == roomId)
                .sorted((a, b) -> String.valueOf(a.get("bedNumber")).compareTo(String.valueOf(b.get("bedNumber"))))
                .toList();
    }

    // ==================== 辅助 ====================

    private long occupiedCount(long roomId) {
        return students().stream()
                .filter(row -> "1".equals(row.get("dormStatus")) && row.get("roomId") != null
                        && ((Number) row.get("roomId")).longValue() == roomId)
                .count();
    }

    private RoomDef roomById(long roomId) {
        return ROOMS.stream()
                .filter(def -> def.roomId() == roomId)
                .findFirst()
                .orElseThrow(() -> new BusinessApiException(404, "演示数据中没有房间：" + roomId));
    }

    private String buildingName(long buildingId) {
        return BUILDINGS.stream()
                .filter(def -> def.buildingId() == buildingId)
                .map(BuildingDef::buildingName)
                .findFirst()
                .orElse("");
    }

    private String floorName(long floorId) {
        return FLOORS.stream()
                .filter(def -> def.floorId() == floorId)
                .map(FloorDef::floorName)
                .findFirst()
                .orElse("");
    }

    private BigDecimal percent(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
