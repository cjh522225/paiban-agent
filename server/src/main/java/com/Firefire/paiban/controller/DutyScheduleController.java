package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.dto.AutoScheduleResult;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.mapper.DutyAdjustmentMakeupMapper;
import com.Firefire.paiban.mapper.DutyAdjustmentMapper;
import com.Firefire.paiban.mapper.DutyCoolingMapper;
import com.Firefire.paiban.service.*;
import com.Firefire.paiban.util.ExcelExporter;
import com.Firefire.paiban.util.ScheduleLogger;
import com.Firefire.paiban.config.LocalScheduleLock;
import com.Firefire.paiban.config.SchedulingLockManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class DutyScheduleController {

    private final DutyScheduleService dutyScheduleService;
    private final UserService userService;
    private final DormitoryService dormitoryService;
    private final ExcelExporter excelExporter;
    private final SwapLogService swapLogService;
    private final SemesterConfigService semesterConfigService;
    private final TimeSlotService timeSlotService;
    private final UserAvailabilityService userAvailabilityService;
    private final HolidayService holidayService;
    private final DutyAdjustmentMapper dutyAdjustmentMapper;
    private final DutyAdjustmentMakeupMapper dutyAdjustmentMakeupMapper;
    private final DutyCoolingMapper dutyCoolingMapper;
    private final LocalScheduleLock localScheduleLock;
    private final DormIdentityPermissionService dormIdentityPermissionService;

    @Autowired(required = false)
    private SchedulingLockManager lockManager;

    @GetMapping
    public Result<List<DutySchedule>> list(@RequestParam String type,
                                            @RequestParam String locationId,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<DutySchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DutySchedule::getType, type)
               .eq(DutySchedule::getLocationId, locationId);
        if (startDate != null) wrapper.ge(DutySchedule::getDutyDate, startDate);
        if (endDate != null) wrapper.le(DutySchedule::getDutyDate, endDate);
        wrapper.orderByAsc(DutySchedule::getDutyDate);
        List<DutySchedule> result = dutyScheduleService.list(wrapper);
        ScheduleLogger.logQuery(type, locationId, startDate, endDate, result);
        return Result.success(result);
    }


    @GetMapping("/my")
    public Result<List<DutySchedule>> myList(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        LambdaQueryWrapper<DutySchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DutySchedule::getUserId, userId);
        if (startDate != null) wrapper.ge(DutySchedule::getDutyDate, startDate);
        if (endDate != null) wrapper.le(DutySchedule::getDutyDate, endDate);
        wrapper.orderByAsc(DutySchedule::getDutyDate);
        return Result.success(dutyScheduleService.list(wrapper));
    }

    @GetMapping("/all")
    public Result<List<DutySchedule>> allList(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<DutySchedule> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) wrapper.ge(DutySchedule::getDutyDate, startDate);
        if (endDate != null) wrapper.le(DutySchedule::getDutyDate, endDate);
        wrapper.orderByAsc(DutySchedule::getDutyDate);
        return Result.success(dutyScheduleService.list(wrapper));
    }

    @PostMapping
    public Result<Void> save(@RequestBody DutySchedule schedule, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        // 手动添加排班：下拉框已保证人员合法（宿舍=身份/性别，办公室=空闲时间），后端不再做约束拦截，管理员意愿优先
        if (schedule.getUserId() == null || schedule.getDutyDate() == null || schedule.getTimeSlot() == null) {
            return Result.error("请填写完整的排班信息");
        }
        User user = userService.getById(schedule.getUserId());
        if (user == null) return Result.error("用户不存在");
        if (schedule.getUserName() == null || schedule.getUserName().isEmpty()) {
            schedule.setUserName(user.getRealName());
        }
        dutyScheduleService.save(schedule);
        return Result.success();
    }

    @PostMapping("/auto")
    public Result<Void> autoSchedule(@RequestBody Map<String, Object> params) {
        String type = (String) params.get("type");
        String locationId = (String) params.get("locationId");
        String locationName = (String) params.get("locationName");

        @SuppressWarnings("unchecked")
        List<Object> userIdsRaw = (List<Object>) params.get("userIds");
        @SuppressWarnings("unchecked")
        List<String> userNamesList = (List<String>) params.get("userNames");

        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        String timeSlot = (String) params.get("timeSlot");

        if (userIdsRaw == null || userIdsRaw.isEmpty()) return Result.error("人员列表不能为空");
        if (userNamesList == null || userNamesList.isEmpty()) return Result.error("人员姓名列表不能为空");

        Long[] userIds = userIdsRaw.stream().map(obj -> ((Number) obj).longValue()).toArray(Long[]::new);
        String[] userNames = userNamesList.toArray(new String[0]);

        dutyScheduleService.autoSchedule(type, locationId, locationName, userIds, userNames, startDate, endDate, timeSlot);
        return Result.success();
    }

    @PostMapping("/auto/dormitory")
    public Result<AutoScheduleResult> autoScheduleDormitory(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) params.get("dormitoryIds");
        List<Long> dormitoryIds = ids != null ? ids.stream().map(o -> ((Number) o).longValue()).collect(java.util.stream.Collectors.toList()) : null;
        String key = buildLockKey("dormitory", dormitoryIds);
        // B修复：进程内互斥(单实例即可防并发/连点；多实例再配合下方 Redis 锁)
        if (!localScheduleLock.tryLock(key)) {
            return Result.error("正在生成排班，请稍后再试");
        }
        try {
            if (lockManager != null && !lockManager.tryLock("dormitory", dormitoryIds, 10, 300)) {
                return Result.error("其他管理员正在生成全体排班，请稍后再试");
            }
            return Result.success(dutyScheduleService.autoScheduleDormitory(params));
        } finally {
            if (lockManager != null) lockManager.unlock("dormitory", dormitoryIds);
            localScheduleLock.unlock(key);
        }
    }

    @PostMapping("/auto/office")
    public Result<AutoScheduleResult> autoScheduleOffice(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) params.get("officeIds");
        List<Long> officeIds = ids != null ? ids.stream().map(o -> ((Number) o).longValue()).collect(java.util.stream.Collectors.toList()) : null;
        String key = buildLockKey("office", officeIds);
        // B修复：进程内互斥(单实例即可防并发/连点；多实例再配合下方 Redis 锁)
        if (!localScheduleLock.tryLock(key)) {
            return Result.error("正在生成排班，请稍后再试");
        }
        try {
            if (lockManager != null && !lockManager.tryLock("office", officeIds, 10, 300)) {
                return Result.error("其他管理员正在生成全体排班，请稍后再试");
            }
            return Result.success(dutyScheduleService.autoScheduleOffice(params));
        } finally {
            if (lockManager != null) lockManager.unlock("office", officeIds);
            localScheduleLock.unlock(key);
        }
    }

    /** 进程内锁的 key：type + 排序后的地点(空/全量则 ALL) */
    private String buildLockKey(String type, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "schedule:" + type + ":ALL";
        String joined = ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return "schedule:" + type + ":loc:" + joined;
    }

    /**
     * 办公室排班"第 week 周"的排班日期列：供前端动态渲染表头（不再固定周一~周五）。
     * 判断规则与 OfficeSchedulingStrategy 完全一致，仅算日期列、不排人：
     * 工作日（周一~周五）遇放假跳过；周末（周六/日）仅补班日排班，
     * 空闲按 replaced_date（补哪天的课）的星期+单双周计算。
     */
    @GetMapping("/office/week-columns")
    public Result<List<Map<String, Object>>> officeWeekColumns(@RequestParam Integer week) {
        SemesterConfig cfg = semesterConfigService.getCurrent();
        LocalDate semesterStart = (cfg != null) ? cfg.getStartDate() : null;
        if (semesterStart == null) {
            return Result.success(new ArrayList<>()); // 未配置学期开始日，无法推算第几周的日期范围
        }
        // 周一锚点：学期开始日所在日历周的周一（与引擎 weekOf / 前端 getWeekMonday 的周一起点一致）
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        LocalDate weekStart = anchor.plusDays((long) (week - 1) * 7);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 调休补课映射：补班日 -> 补哪天的课（与引擎一致）
        Map<LocalDate, LocalDate> makeupMap = new HashMap<>();
        for (DutyAdjustmentMakeup mk : dutyAdjustmentMakeupMapper.selectList(null)) {
            if (mk.getMakeupDate() != null && mk.getReplacedDate() != null) {
                makeupMap.put(mk.getMakeupDate(), mk.getReplacedDate());
            }
        }
        // 循环起点扩展到与本周边界相交的调休周期开始（与引擎 loopStart 扩展一致）
        LocalDate loopStart = weekStart;
        for (DutyAdjustment adj : dutyAdjustmentMapper.selectList(null)) {
            if (adj.getStartDate() != null && adj.getEndDate() != null
                    && !adj.getStartDate().isAfter(weekEnd) && !adj.getEndDate().isBefore(weekStart)
                    && adj.getStartDate().isBefore(loopStart)) {
                loopStart = adj.getStartDate();
            }
        }

        List<Map<String, Object>> columns = new ArrayList<>();
        for (LocalDate d = loopStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
            // 只取目标周内的日期（扩展出的上周日期不属于本周列）
            if (officeWeekOf(d, semesterStart) != week) continue;
            int dow = d.getDayOfWeek().getValue();
            boolean isWeekend = (dow == DayOfWeek.SATURDAY.getValue() || dow == DayOfWeek.SUNDAY.getValue());
            LocalDate replaced = makeupMap.get(d);
            // 工作日放假不排 / 周末非补班不排（与引擎完全一致）
            if (!isWeekend && holidayService.isHoliday(d)) continue;
            if (isWeekend && replaced == null) continue;

            Map<String, Object> col = new LinkedHashMap<>();
            col.put("date", d.toString());
            col.put("weekday", chineseWeekday(dow));
            if (isWeekend) {
                col.put("isMakeup", true);
                col.put("makeupLabel", chineseWeekday(replaced.getDayOfWeek().getValue())
                        + "(" + replaced.getMonthValue() + "/" + replaced.getDayOfMonth() + ")");
                col.put("effectiveDow", replaced.getDayOfWeek().getValue());
                col.put("effectiveParity", officeWeekParity(replaced, semesterStart));
            } else {
                col.put("isMakeup", false);
                col.put("makeupLabel", null);
                col.put("effectiveDow", dow);
                col.put("effectiveParity", officeWeekParity(d, semesterStart));
            }
            columns.add(col);
        }
        return Result.success(columns);
    }

    /** 中文周几（1=周一 .. 7=周日） */
    private String chineseWeekday(int dow) {
        switch (dow) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            default: return "周日";
        }
    }

    /** 与 OfficeSchedulingStrategy.weekOf 一致：按学期开始日推算 date 所在周次（周一为一周起点） */
    private int officeWeekOf(LocalDate date, LocalDate semesterStart) {
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        long week = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
        return week < 1 ? 1 : (int) week;
    }

    /** 与 OfficeSchedulingStrategy.weekParity 一致：奇数周 odd / 偶数周 even */
    private String officeWeekParity(LocalDate date, LocalDate semesterStart) {
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        long week = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
        return (week % 2 == 1) ? "odd" : "even";
    }

    /** 手动互换：交换两条排班记录的人员，并写换班日志 */
    @PostMapping("/swap")
    public Result<Void> swap(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        Object aIdRaw = params.get("scheduleIdA");
        Object bIdRaw = params.get("scheduleIdB");
        if (aIdRaw == null || bIdRaw == null) return Result.error("请选择两个要互换的班次");
        Long scheduleIdA = ((Number) aIdRaw).longValue();
        Long scheduleIdB = ((Number) bIdRaw).longValue();
        if (scheduleIdA.equals(scheduleIdB)) return Result.error("不能选同一个班次");

        DutySchedule a = dutyScheduleService.getById(scheduleIdA);
        DutySchedule b = dutyScheduleService.getById(scheduleIdB);
        if (a == null || b == null) return Result.error("排班记录不存在");
        if (!a.getType().equals(b.getType())) {
            return Result.error("只能同类排班互换（宿舍↔宿舍、办公室↔办公室）");
        }
        if (a.getUserId().equals(b.getUserId())) {
            return Result.error("两个班次是同一人，无需互换");
        }

        // 关键：互换前校验互换后双方是否符合已明确的约束（宿舍身份/性别/当天不重复）
        String errA = validateSwapTarget(a, b.getUserId(), a.getId(), b.getId());
        if (errA != null) return Result.error(errA);
        String errB = validateSwapTarget(b, a.getUserId(), a.getId(), b.getId());
        if (errB != null) return Result.error(errB);

        // 交换双方人员
        Long aUid = a.getUserId(); String aName = a.getUserName();
        Long bUid = b.getUserId(); String bName = b.getUserName();
        a.setUserId(bUid); a.setUserName(bName);
        b.setUserId(aUid); b.setUserName(aName);
        dutyScheduleService.updateById(a);
        dutyScheduleService.updateById(b);

        // 巡班每天全局1人（同一天所有宿舍楼共用同一巡班人）：互换涉及巡班时，同步同天其他宿舍楼的巡班记录，保持全局一致
        if ("dormitory".equals(a.getType()) && "巡班".equals(a.getTimeSlot())) {
            dutyScheduleService.update(new LambdaUpdateWrapper<DutySchedule>()
                .set(DutySchedule::getUserId, bUid)
                .set(DutySchedule::getUserName, bName)
                .eq(DutySchedule::getType, "dormitory")
                .eq(DutySchedule::getTimeSlot, "巡班")
                .eq(DutySchedule::getDutyDate, a.getDutyDate())
                .eq(DutySchedule::getUserId, aUid)
                .ne(DutySchedule::getId, a.getId()));
        }
        if ("dormitory".equals(b.getType()) && "巡班".equals(b.getTimeSlot())) {
            dutyScheduleService.update(new LambdaUpdateWrapper<DutySchedule>()
                .set(DutySchedule::getUserId, aUid)
                .set(DutySchedule::getUserName, aName)
                .eq(DutySchedule::getType, "dormitory")
                .eq(DutySchedule::getTimeSlot, "巡班")
                .eq(DutySchedule::getDutyDate, b.getDutyDate())
                .eq(DutySchedule::getUserId, bUid)
                .ne(DutySchedule::getId, b.getId()));
        }

        // 写换班日志（记录互换后的双方岗位/人员/操作人）
        Integer weekNumber = params.get("weekNumber") != null
            ? ((Number) params.get("weekNumber")).intValue() : computeWeekNumber(a.getDutyDate());
        SwapLog log = new SwapLog();
        log.setType(a.getType());
        log.setWeekNumber(weekNumber);
        log.setScheduleIdA(a.getId()); log.setUserIdA(a.getUserId()); log.setUserNameA(a.getUserName());
        log.setScheduleIdB(b.getId()); log.setUserIdB(b.getUserId()); log.setUserNameB(b.getUserName());
        log.setOperatorId((Long) request.getAttribute("userId"));
        swapLogService.save(log);
        return Result.success();
    }

    /**
     * 校验"某条排班记录换成某人来顶替"是否合规：
     * ① 同类型同楼同一天不重复（排除正在互换的两条记录）；
     * ② 宿舍排班按岗位校验值班身份与性别匹配（巡班/坐班/敲灯）。
     * 办公室排班无身份/性别限制，仅做①。
     */
    private String validateSwapTarget(DutySchedule record, Long newUserId, Long excludeIdA, Long excludeIdB) {
        User user = userService.getById(newUserId);
        if (user == null) return "用户不存在";

        // ① 同天同楼不重复：该用户当天在该地点不能已有其他班次
        LambdaQueryWrapper<DutySchedule> dupWrapper = new LambdaQueryWrapper<DutySchedule>()
            .eq(DutySchedule::getType, record.getType())
            .eq(DutySchedule::getLocationId, record.getLocationId())
            .eq(DutySchedule::getDutyDate, record.getDutyDate())
            .eq(DutySchedule::getUserId, newUserId)
            .notIn(DutySchedule::getId, excludeIdA, excludeIdB);
        // 巡班互换场景：换入者去当坐班/敲灯时，该用户当天在该楼的"巡班"记录会随全局同步一并换走，
        // 不算冲突；但换入者去当巡班时仍严格查重（巡班每天全局仅 1 人）
        if ("dormitory".equals(record.getType())
                && ("坐班".equals(record.getTimeSlot()) || "敲灯".equals(record.getTimeSlot()))) {
            dupWrapper.ne(DutySchedule::getTimeSlot, "巡班");
        }
        Long dup = dutyScheduleService.count(dupWrapper);
        if (dup > 0) {
            return user.getRealName() + " 当天已在该处排班，不能互换";
        }

        // ② 宿舍身份/性别约束
        if ("dormitory".equals(record.getType())) {
            Dormitory dorm = dormitoryService.getById(Long.parseLong(record.getLocationId()));
            if (dorm == null) return "宿舍楼不存在";
            String slot = record.getTimeSlot();
            // 身份资格按"身份权限授权"判断（默认对角：巡班仅巡班岗、坐班仅坐班岗、敲灯仅敲灯岗）
            if ("坐班".equals(slot)) {
                if (!dorm.getGender().equals(user.getGender())) {
                    return user.getRealName() + " 性别与" + dorm.getName() + "不符，不能担任坐班";
                }
                if (!dormIdentityPermissionService.canFill(user.getDutyRole(), "坐班")) {
                    return user.getRealName() + " 未被授权坐班岗位，不能互换";
                }
            } else if ("巡班".equals(slot)) {
                if (!dormIdentityPermissionService.canFill(user.getDutyRole(), "巡班")) {
                    return user.getRealName() + " 未被授权巡班岗位，不能互换";
                }
            } else if ("敲灯".equals(slot)) {
                if (!dorm.getGender().equals(user.getGender())) {
                    return user.getRealName() + " 性别与" + dorm.getName() + "不符，不能担任敲灯";
                }
                if (!dormIdentityPermissionService.canFill(user.getDutyRole(), "敲灯")) {
                    return user.getRealName() + " 未被授权敲灯岗位，不能互换";
                }
            }
        }

        // ③ 办公室：换入者在目标(星期×时段)必须已设置空闲时间（含单/双周与"不限"）
        if ("office".equals(record.getType())) {
            int dow = record.getDutyDate().getDayOfWeek().getValue(); // 1=周一..7=周日
            String parity = (computeWeekNumber(record.getDutyDate()) % 2 == 1) ? "odd" : "even";
            List<Long> slotIds = timeSlotService.list(
                    new LambdaQueryWrapper<TimeSlot>().eq(TimeSlot::getLabel, record.getTimeSlot()))
                .stream().map(TimeSlot::getId).toList();
            if (!slotIds.isEmpty()) {
                Long avail = userAvailabilityService.count(new LambdaQueryWrapper<UserAvailability>()
                    .eq(UserAvailability::getUserId, newUserId)
                    .eq(UserAvailability::getDayOfWeek, dow)
                    .in(UserAvailability::getTimeSlotId, slotIds)
                    .and(w -> w.eq(UserAvailability::getWeekParity, parity)
                        .or().eq(UserAvailability::getWeekParity, "both")));
                if (avail == null || avail == 0) {
                    return user.getRealName() + " 在目标时段（" + record.getTimeSlot() + "）未设置空闲时间，不能互换";
                }
            }
        }
        return null;
    }

    /** 由排班日期推算第几周（前端未传周次时的兜底） */
    private Integer computeWeekNumber(LocalDate date) {
        try {
            SemesterConfig cfg = semesterConfigService.getCurrent();
            if (cfg != null && cfg.getStartDate() != null) {
                long weeks = ChronoUnit.DAYS.between(cfg.getStartDate(), date) / 7 + 1;
                return (int) Math.max(1, weeks);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    @GetMapping("/export/dormitory")
    public ResponseEntity<byte[]> exportDormitory(@RequestParam String startDate,
                                                  @RequestParam String endDate,
                                                  @RequestParam(required = false) Integer week) throws Exception {
        // 只导出当前周、所有宿舍楼的排班记录
        List<DutySchedule> schedules = dutyScheduleService.list(
            new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, "dormitory")
                .ge(DutySchedule::getDutyDate, startDate)
                .le(DutySchedule::getDutyDate, endDate)
                .orderByAsc(DutySchedule::getLocationId)
                .orderByAsc(DutySchedule::getDutyDate));

        String sheetName = (week != null ? "第" + week + "周" : "") + "宿舍值班安排";
        byte[] data = excelExporter.exportDormitorySchedule(schedules, sheetName);

        return createResponse(data, sheetName + ".xlsx");
    }

    @GetMapping("/export/office")
    public ResponseEntity<byte[]> exportOffice(@RequestParam String startDate,
                                               @RequestParam String endDate,
                                               @RequestParam(required = false) Integer week) throws Exception {
        // 导出当前周、所有办公室的排班记录
        List<DutySchedule> schedules = dutyScheduleService.list(
            new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, "office")
                .ge(DutySchedule::getDutyDate, startDate)
                .le(DutySchedule::getDutyDate, endDate)
                .orderByAsc(DutySchedule::getLocationId)
                .orderByAsc(DutySchedule::getDutyDate));

        String sheetName = (week != null ? "第" + week + "周" : "") + "办公室值班安排";
        List<String> timeSlotOrder = List.of("1-2 节", "3-4 节", "5-6 节", "7-8 节");
        byte[] data = excelExporter.exportOfficeSchedule(schedules, sheetName, timeSlotOrder);

        return createResponse(data, sheetName + ".xlsx");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        DutySchedule s = dutyScheduleService.getById(id);
        if (s != null) {
            dutyScheduleService.removeById(id);
            // A修复：删排班联动清冷却，避免原班人被"连坐下周"
            clearCoolingIfUnused(s);
        }
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        String type = (String) params.get("type");
        String locationId = (String) params.get("locationId");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        LambdaQueryWrapper<DutySchedule> wrapper = new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, type)
                .eq(DutySchedule::getLocationId, locationId);
        // 只清空指定日期范围内的记录，不动其他周的历史
        if (startDate != null) wrapper.ge(DutySchedule::getDutyDate, startDate);
        if (endDate != null) wrapper.le(DutySchedule::getDutyDate, endDate);
        List<DutySchedule> affected = dutyScheduleService.list(wrapper);
        dutyScheduleService.remove(wrapper);
        // A修复：删/清空排班联动清冷却，避免原班人被"连坐下周"
        for (DutySchedule s : affected) clearCoolingIfUnused(s);
        return Result.success();
    }

    /** 与自动排班引擎同口径：按"学期开始日归位到其所在周一"为锚推算 date 的周次（引擎写冷却用同一算法） */
    private int engineWeekOf(LocalDate date) {
        try {
            SemesterConfig cfg = semesterConfigService.getCurrent();
            if (cfg != null && cfg.getStartDate() != null) {
                LocalDate start = cfg.getStartDate();
                LocalDate anchor = start.minusDays(start.getDayOfWeek().getValue() - 1L);
                long w = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
                return (int) Math.max(1, w);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    /** 该用户该周(以周一~周日为一周)是否已无任何排班(宿舍/办公室都算) */
    private boolean userStillHasDutyThatWeek(Long userId, int week) {
        try {
            SemesterConfig cfg = semesterConfigService.getCurrent();
            if (cfg == null || cfg.getStartDate() == null) return false;
            LocalDate anchor = cfg.getStartDate().minusDays(cfg.getStartDate().getDayOfWeek().getValue() - 1L);
            LocalDate ws = anchor.plusDays((week - 1L) * 7L);
            LocalDate we = ws.plusDays(6);
            long c = dutyScheduleService.count(new LambdaQueryWrapper<DutySchedule>()
                    .eq(DutySchedule::getUserId, userId)
                    .ge(DutySchedule::getDutyDate, ws)
                    .le(DutySchedule::getDutyDate, we));
            return c > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 删除排班后的冷却联动：仅当该用户该周已无任何排班时，才清掉其对应周冷却 */
    private void clearCoolingIfUnused(DutySchedule s) {
        if (s.getUserId() == null || s.getDutyDate() == null) return;
        int w = engineWeekOf(s.getDutyDate());
        if (!userStillHasDutyThatWeek(s.getUserId(), w)) {
            dutyCoolingMapper.delete(new LambdaQueryWrapper<DutyCooling>()
                    .eq(DutyCooling::getUserId, s.getUserId())
                    .eq(DutyCooling::getWeekNumber, w));
        }
    }

    private ResponseEntity<byte[]> createResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
