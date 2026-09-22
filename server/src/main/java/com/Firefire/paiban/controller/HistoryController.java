package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.service.*;
import com.Firefire.paiban.util.ExcelExporter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final DutyScheduleService dutyScheduleService;
    private final DormitoryService dormitoryService;
    private final OfficeService officeService;
    private final SemesterConfigService semesterConfigService;
    private final ExcelExporter excelExporter;

    @GetMapping("/semesters")
    public Result<List<SemesterConfig>> getSemesters() {
        return Result.success(semesterConfigService.list());
    }

    @GetMapping("/weeks")
    public Result<List<Map<String, Object>>> getPastWeeks(@RequestParam String startDateStr) {
        LocalDate semesterStart = LocalDate.parse(startDateStr);
        // 归一化到开学日所在日历周的周一，保证"第N周"范围与排班页一致，不随开学日星期几变化
        semesterStart = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1L);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> weeks = new ArrayList<>();
        LocalDate weekStart = semesterStart;
        int weekNum = 1;
        // 包含本周（今天是周一也能查到本周）
        while (!weekStart.isAfter(today)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            Map<String, Object> w = new HashMap<>();
            w.put("weekNum", weekNum);
            w.put("startDate", weekStart.toString());
            w.put("endDate", weekEnd.toString());
            weeks.add(w);
            weekStart = weekStart.plusDays(7);
            weekNum++;
        }
        return Result.success(weeks);
    }

    @GetMapping("/dormitory")
    public Result<List<Map<String, Object>>> getDormitoryHistory(
            @RequestParam String startDate, @RequestParam String endDate,
            @RequestParam(required = false) String locationId) {
        return Result.success(buildDormitoryHistory(startDate, endDate, locationId));
    }

    /** 宿舍历史明细：按楼分组，每天 patrol/sitting/knockLights。页面与导出共用同一份数据，保证一致。 */
    private List<Map<String, Object>> buildDormitoryHistory(String startDate, String endDate, String locationId) {
        LambdaQueryWrapper<DutySchedule> w = new LambdaQueryWrapper<DutySchedule>()
            .eq(DutySchedule::getType, "dormitory")
            .ge(DutySchedule::getDutyDate, startDate)
            .le(DutySchedule::getDutyDate, endDate);
        if (locationId != null && !locationId.isEmpty()) w.eq(DutySchedule::getLocationId, locationId);
        List<DutySchedule> schedules = dutyScheduleService.list(w.orderByAsc(DutySchedule::getDutyDate));

        // 按宿舍楼真实名称兜底：手动添加时 location_name 可能带(男)/(女)后缀，导致按名称分组裂开
        Map<String, String> dormNameMap = new HashMap<>();
        for (Dormitory d : dormitoryService.list()) {
            dormNameMap.put(String.valueOf(d.getId()), d.getName());
        }

        Map<String, Map<String, Map<String, List<String>>>> grouped = new LinkedHashMap<>();
        Map<String, String> locIdToName = new LinkedHashMap<>();
        for (DutySchedule ds : schedules) {
            String locId = ds.getLocationId();
            String date = ds.getDutyDate().toString();
            String role = ds.getTimeSlot();
            locIdToName.putIfAbsent(locId, ds.getLocationName());
            grouped.computeIfAbsent(locId, k -> new LinkedHashMap<>())
                .computeIfAbsent(date, k -> new HashMap<>())
                .computeIfAbsent(role, k -> new ArrayList<>())
                .add(ds.getUserName());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<String>>>> locEntry : grouped.entrySet()) {
            Map<String, Object> locData = new HashMap<>();
            String locId = locEntry.getKey();
            locData.put("locationName", dormNameMap.getOrDefault(locId, locIdToName.getOrDefault(locId, locId)));
            List<Map<String, Object>> days = new ArrayList<>();
            for (Map.Entry<String, Map<String, List<String>>> dateEntry : locEntry.getValue().entrySet()) {
                Map<String, Object> day = new HashMap<>();
                day.put("date", dateEntry.getKey());
                Map<String, List<String>> roles = dateEntry.getValue();
                day.put("patrol", roles.getOrDefault("巡班", Collections.emptyList()));
                day.put("sitting", roles.getOrDefault("坐班", Collections.emptyList()));
                day.put("knockLights", roles.getOrDefault("敲灯", Collections.emptyList()));
                days.add(day);
            }
            locData.put("days", days);
            result.add(locData);
        }
        return result;
    }

    @GetMapping("/office")
    public Result<List<Map<String, Object>>> getOfficeHistory(
            @RequestParam String startDate, @RequestParam String endDate,
            @RequestParam(required = false) String locationId) {
        return Result.success(buildOfficeHistory(startDate, endDate, locationId));
    }

    /** 办公室历史明细：按楼分组，每天 slots（时段→人员）。页面与导出共用同一份数据，保证一致。 */
    private List<Map<String, Object>> buildOfficeHistory(String startDate, String endDate, String locationId) {
        LambdaQueryWrapper<DutySchedule> w = new LambdaQueryWrapper<DutySchedule>()
            .eq(DutySchedule::getType, "office")
            .ge(DutySchedule::getDutyDate, startDate)
            .le(DutySchedule::getDutyDate, endDate);
        if (locationId != null && !locationId.isEmpty()) w.eq(DutySchedule::getLocationId, locationId);
        List<DutySchedule> schedules = dutyScheduleService.list(w.orderByAsc(DutySchedule::getDutyDate));

        Map<String, String> officeNameMap = new HashMap<>();
        for (Office o : officeService.list()) {
            officeNameMap.put(String.valueOf(o.getId()), o.getName());
        }

        Map<String, Map<String, Map<String, List<String>>>> grouped = new LinkedHashMap<>();
        Map<String, String> locIdToName = new LinkedHashMap<>();
        for (DutySchedule ds : schedules) {
            String locId = ds.getLocationId();
            String date = ds.getDutyDate().toString();
            String slot = ds.getTimeSlot();
            locIdToName.putIfAbsent(locId, ds.getLocationName());
            grouped.computeIfAbsent(locId, k -> new LinkedHashMap<>())
                .computeIfAbsent(date, k -> new HashMap<>())
                .computeIfAbsent(slot, k -> new ArrayList<>())
                .add(ds.getUserName());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<String>>>> locEntry : grouped.entrySet()) {
            Map<String, Object> locData = new HashMap<>();
            String locId = locEntry.getKey();
            locData.put("locationName", officeNameMap.getOrDefault(locId, locIdToName.getOrDefault(locId, locId)));
            List<Map<String, Object>> days = new ArrayList<>();
            for (Map.Entry<String, Map<String, List<String>>> dateEntry : locEntry.getValue().entrySet()) {
                Map<String, Object> day = new HashMap<>();
                day.put("date", dateEntry.getKey());
                day.put("slots", dateEntry.getValue());
                days.add(day);
            }
            locData.put("days", days);
            result.add(locData);
        }
        return result;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportHistory(
            @RequestParam String type,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) Integer week) throws Exception {
        List<Map<String, Object>> history;
        List<String> timeSlots = null;
        String weekPart = week != null ? "_第" + week + "周" : "";
        if ("dormitory".equals(type)) {
            history = buildDormitoryHistory(startDate, endDate, locationId);
        } else {
            history = buildOfficeHistory(startDate, endDate, locationId);
            timeSlots = List.of("1-2 节", "3-4 节", "5-6 节", "7-8 节");
        }
        String sheetName = "历史记录（" + ("dormitory".equals(type) ? "宿舍" : "办公室") + "）" + weekPart;
        byte[] data = excelExporter.exportHistory(history, type, timeSlots, sheetName);
        return createResponse(data, sheetName + ".xlsx");
    }

    private ResponseEntity<byte[]> createResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
