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
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final UserService userService;
    private final DutyScheduleService dutyScheduleService;
    private final LeaveRequestService leaveRequestService;
    private final MessageService messageService;
    private final DormitoryService dormitoryService;
    private final OfficeService officeService;
    private final ExcelExporter excelExporter;

    @GetMapping("/admin")
    public Result<Map<String, Object>> getAdminStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("dormitoryCount", dormitoryService.count(new LambdaQueryWrapper<Dormitory>().eq(Dormitory::getStatus, 1)));
        statistics.put("officeCount", officeService.count(new LambdaQueryWrapper<Office>().eq(Office::getStatus, 1)));
        statistics.put("staffCount", userService.count(new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        statistics.put("pendingLeaveCount", leaveRequestService.count(new LambdaQueryWrapper<LeaveRequest>().eq(LeaveRequest::getStatus, "pending")));

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(4);
        statistics.put("weekDutyCount", dutyScheduleService.count(
            new LambdaQueryWrapper<DutySchedule>().ge(DutySchedule::getDutyDate, weekStart).le(DutySchedule::getDutyDate, weekEnd)));
        statistics.put("messageCount", messageService.count(new LambdaQueryWrapper<Message>().eq(Message::getStatus, 1)));

        return Result.success(statistics);
    }

    @GetMapping("/pending-leaves")
    public Result<List<LeaveRequest>> getPendingLeaves() {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaveRequest::getStatus, "pending").orderByDesc(LeaveRequest::getCreateTime).last("LIMIT 5");
        return Result.success(leaveRequestService.list(wrapper));
    }

    @GetMapping("/recent-messages")
    public Result<List<Message>> getRecentMessages() {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getStatus, 1).orderByDesc(Message::getCreateTime).last("LIMIT 5");
        return Result.success(messageService.list(wrapper));
    }

    @GetMapping("/duty-counts")
    public Result<List<Map<String, Object>>> getDutyCounts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(buildDutyCounts(type, startDate, endDate));
    }

    /** 统计逻辑：按人统计值班次数（宿舍分岗位，办公室只算总数）。页面与导出共用同一份数据，保证一致。 */
    private List<Map<String, Object>> buildDutyCounts(String type, String startDate, String endDate) {
        LambdaQueryWrapper<DutySchedule> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(DutySchedule::getDutyDate, LocalDate.parse(startDate));
        }
        if (endDate != null) {
            wrapper.le(DutySchedule::getDutyDate, LocalDate.parse(endDate));
        }
        if (startDate == null && endDate == null) {
            wrapper.lt(DutySchedule::getDutyDate, LocalDate.now());
        }
        if (type != null) wrapper.eq(DutySchedule::getType, type);
        List<DutySchedule> all = dutyScheduleService.list(wrapper);

        Map<String, Map<String, Object>> resultMap = new LinkedHashMap<>();
        Map<String, Set<LocalDate>> patrolDates = new HashMap<>();

        for (DutySchedule ds : all) {
            String key = ds.getUserId() + "-" + ds.getUserName();
            resultMap.computeIfAbsent(key, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", ds.getUserId());
                m.put("userName", ds.getUserName());
                m.put("totalCount", 0L);
                return m;
            });
            Map<String, Object> entry = resultMap.get(key);

            if ("巡班".equals(ds.getTimeSlot())) {
                String pk = ds.getUserId() + "-" + ds.getDutyDate();
                Set<LocalDate> dates = patrolDates.computeIfAbsent(key, k -> new HashSet<>());
                if (dates.add(ds.getDutyDate())) {
                    entry.put("totalCount", ((Long) entry.get("totalCount")) + 1);
                    entry.merge(ds.getTimeSlot(), 1L, (a, b) -> (Long) a + (Long) b);
                }
            } else {
                entry.put("totalCount", ((Long) entry.get("totalCount")) + 1);
                entry.merge(ds.getTimeSlot(), 1L, (a, b) -> (Long) a + (Long) b);
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    @GetMapping("/export/duty-counts")
    public ResponseEntity<byte[]> exportDutyCounts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) throws Exception {
        List<Map<String, Object>> counts = buildDutyCounts(type, startDate, endDate);
        String sheetName = "dormitory".equals(type) ? "宿舍值班统计" : "办公室值班统计";
        byte[] data = excelExporter.exportDutyCounts(counts, type, sheetName);
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
