package com.Firefire.paiban.scheduling;

import com.Firefire.paiban.dto.AutoScheduleResult;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.mapper.DutyAdjustmentMapper;
import com.Firefire.paiban.mapper.DutyCoolingMapper;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.Firefire.paiban.mapper.MultiDutyRequestMapper;
import com.Firefire.paiban.mapper.SchedulePointerMapper;
import com.Firefire.paiban.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 宿舍排班（指针循环 + 冷却/多排/回捞方案）：
 * 每个岗位身份一个"池"（按用户ID顺序），一根指针从表头往下排，排完一圈回表头；
 * 指针持久化到 schedule_pointer 表，跨周继续。
 * 每池选人分三级：
 *   1) 多排优先：申请了"宿舍多排"且已批准、覆盖本周的人优先选
 *      （当天一人一次，不限制一周多次，不进冷却表）；
 *   2) 正常选：指针循环，排除冷却表（上周排过，防两周连排）+ 当天请假 + 本周已选；
 *   3) 回捞：人不够时从冷却表按指针轮流捞（回捞的人本周也算已排，周次更新为本周重新冷却）。
 * 冷却表（duty_cooling）记录最近两周排过的人，隔一周删除冷却结束的记录；
 * 排班按周处理：每周结束把本周排的普通人写入冷却表。
 */
@Component
@RequiredArgsConstructor
public class DormitorySchedulingStrategy implements SchedulingStrategy {

    private final DormitoryService dormitoryService;
    private final UserService userService;
    private final DutyScheduleMapper dutyScheduleMapper;
    private final LeaveRequestService leaveRequestService;
    private final HolidayService holidayService;
    private final SchedulePointerMapper schedulePointerMapper;
    private final DutyAdjustmentMapper dutyAdjustmentMapper;
    private final DutyCoolingMapper dutyCoolingMapper;
    private final MultiDutyRequestMapper multiDutyRequestMapper;
    private final SemesterConfigService semesterConfigService;
    private final DormIdentityPermissionService dormIdentityPermissionService;

    private static final String TYPE = "dormitory";
    /** 冷却表批量插入每批条数 */
    private static final int BATCH_SIZE = 200;

    private static final String POOL_PATROL = "dorm_patrol";
    private static final String POOL_SITTING_MALE = "dorm_sitting_male";
    private static final String POOL_SITTING_FEMALE = "dorm_sitting_female";
    private static final String POOL_KNOCK_MALE = "dorm_knock_male";
    private static final String POOL_KNOCK_FEMALE = "dorm_knock_female";

    @Override
    public String getType() { return TYPE; }

    @Override
    @SuppressWarnings("unchecked")
    public AutoScheduleResult generate(Map<String, Object> params) {
        List<?> rawIds = (List<?>) params.get("dormitoryIds");
        List<Long> dormitoryIds = rawIds != null ? rawIds.stream()
            .map(o -> ((Number) o).longValue()).collect(Collectors.toList()) : null;
        String startDateStr = (String) params.get("startDate");
        String endDateStr = (String) params.get("endDate");

        // 学期开始日（算周次用）：优先用前端传入，其次用学期配置
        LocalDate semesterStart = params.get("semesterStart") != null
            ? LocalDate.parse((String) params.get("semesterStart")) : null;
        if (semesterStart == null) {
            SemesterConfig cfg = semesterConfigService.getCurrent();
            if (cfg != null && cfg.getStartDate() != null) semesterStart = cfg.getStartDate();
        }
        LocalDate openingEve = semesterStart != null ? semesterStart.minusDays(1) : null;

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        List<Dormitory> allDormitories = loadDormitories(dormitoryIds);
        List<User> allStaff = loadAllStaff();

        // 5 个身份池：按用户ID顺序（表头→表尾）。可顶岗位由"身份权限"授权决定（默认对角，巡班仅巡班岗等）
        Map<String, Set<String>> allowed = dormIdentityPermissionService.allowedByIdentity();
        java.util.function.BiPredicate<User, String> canFill = (u, slot) ->
                u.getDutyRole() != null && allowed.getOrDefault(u.getDutyRole(), Collections.emptySet()).contains(slot);
        List<User> patrolPool = sorted(allStaff.stream()
            .filter(u -> canFill.test(u, "巡班")).collect(Collectors.toList()));
        List<User> sittingMale = sorted(allStaff.stream()
            .filter(u -> "男".equals(u.getGender()) && canFill.test(u, "坐班")).collect(Collectors.toList()));
        List<User> sittingFemale = sorted(allStaff.stream()
            .filter(u -> "女".equals(u.getGender()) && canFill.test(u, "坐班")).collect(Collectors.toList()));
        List<User> knockMale = sorted(allStaff.stream()
            .filter(u -> "男".equals(u.getGender()) && canFill.test(u, "敲灯")).collect(Collectors.toList()));
        List<User> knockFemale = sorted(allStaff.stream()
            .filter(u -> "女".equals(u.getGender()) && canFill.test(u, "敲灯")).collect(Collectors.toList()));

        // 读取各池指针（跨周持续）
        Map<String, Long> pointers = loadPointers();

        // 按天索引请假用户（当天请假跳过）
        Map<LocalDate, Set<Long>> leaveByDate = loadLeaveByDate(startDate, endDate);

        // 调休：宿舍值班 = 调休周期[开始前一天, 结束前一天]。
        // 排班范围 = 请求范围 与 所有与请求相交的调休覆盖范围 的并集（多个调休都排上）；
        // 逐日判断：落在调休覆盖范围内(adjustmentDays)的天必排，其余走正常宿舍规则。
        Set<LocalDate> adjustmentDays = new HashSet<>();
        LocalDate loopStart = startDate;
        LocalDate loopEnd = endDate;
        for (DutyAdjustment adj : dutyAdjustmentMapper.selectList(null)) {
            if (adj.getStartDate() == null || adj.getEndDate() == null) continue;
            LocalDate adjStart = adj.getStartDate().minusDays(1);
            LocalDate adjEnd = adj.getEndDate().minusDays(1);
            if (adjStart.isAfter(endDate) || adjEnd.isBefore(startDate)) continue; // 与请求范围无交集，忽略
            for (LocalDate d = adjStart; !d.isAfter(adjEnd); d = d.plusDays(1)) adjustmentDays.add(d);
            if (adjStart.isBefore(loopStart)) loopStart = adjStart;
            if (adjEnd.isAfter(loopEnd)) loopEnd = adjEnd;
        }

        // 按周次分组：冷却按周维护、一周一人一次按周重置
        TreeMap<Integer, List<LocalDate>> byWeek = new TreeMap<>();
        for (LocalDate d = loopStart; !d.isAfter(loopEnd); d = d.plusDays(1)) {
            byWeek.computeIfAbsent(weekOf(d, semesterStart), k -> new ArrayList<>()).add(d);
        }

        List<DutySchedule> schedules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<Integer, List<LocalDate>> weekEntry : byWeek.entrySet()) {
            int week = weekEntry.getKey();
            List<LocalDate> dates = weekEntry.getValue();
            // 清理冷却已结束的记录（本周-1 之前），隔一周冷却结束
            dutyCoolingMapper.deleteBeforeWeek(week - 1);
            // 冷却排除：本周-1 及之后的（删除后只剩上周，防两周连排）
            Set<Long> coolingIds = loadCoolingIds(week - 1);
            // 多排：宿舍类型、已批准、覆盖本周 -> 每人每周可排天数上限（单周2天/跨周1天）
            Map<Long, Integer> multiLimits = loadMultiLimits(week);
            // 本周多排用户已排天数（达到上限后本周不再排；新一周清零重算）
            Map<Long, Integer> multiPicked = new HashMap<>();
            // 本周已选普通人（一周一人只排一次；多排用户豁免）
            Set<Long> sessionSelected = new HashSet<>();
            // 本周回捞的人（本就在冷却表，周次更新为本周，而非新增）
            Set<Long> recalledSet = new HashSet<>();
            // 本周要写入冷却表的人（普通新排）
            List<DutyCooling> coolingInsert = new ArrayList<>();

            for (LocalDate date : dates) {
                // 逐日判断：调休覆盖日必排；否则按正常规则（跳过周五/周六、假期、假期前一天；假期最后一天照排）
                if (!adjustmentDays.contains(date)) {
                    boolean mustSchedule = holidayService.isLastDayOfHoliday(date);
                    if (!mustSchedule && ((isWeekend(date) && !date.equals(openingEve))
                            || holidayService.isHoliday(date) || holidayService.isDayBeforeHoliday(date))) continue;
                }
                Set<Long> dayLeave = leaveByDate.getOrDefault(date, Collections.emptySet());
                // 当天已排（多排用户也遵守当天一人一次）
                Set<Long> dailySelected = new HashSet<>();

                // 巡班：每天全局1人，不分男女，所有宿舍楼共用
                User patrol = pickNext(patrolPool, pointers, POOL_PATROL, dayLeave, null,
                        sessionSelected, coolingIds, multiLimits, multiPicked, dailySelected, recalledSet);
                if (patrol == null) { warnings.add(date + " 巡班无人可用"); continue; }
                for (Dormitory dorm : allDormitories) {
                    schedules.add(build(date, patrol, dorm, "巡班"));

                    boolean male = "男".equals(dorm.getGender());
                    List<User> sittingPool = male ? sittingMale : sittingFemale;
                    List<User> knockPool = male ? knockMale : knockFemale;
                    String sittingKey = male ? POOL_SITTING_MALE : POOL_SITTING_FEMALE;
                    String knockKey = male ? POOL_KNOCK_MALE : POOL_KNOCK_FEMALE;

                    // 坐班：与宿舍楼性别一致
                    User sitting = pickNext(sittingPool, pointers, sittingKey, dayLeave, null,
                            sessionSelected, coolingIds, multiLimits, multiPicked, dailySelected, recalledSet);
                    if (sitting != null) {
                        schedules.add(build(date, sitting, dorm, "坐班"));
                    } else {
                        warnings.add(date + " " + dorm.getName() + " 坐班无人可用");
                    }

                    // 敲灯：两人，与宿舍楼性别一致
                    User knock1 = pickNext(knockPool, pointers, knockKey, dayLeave, null,
                            sessionSelected, coolingIds, multiLimits, multiPicked, dailySelected, recalledSet);
                    if (knock1 != null) {
                        schedules.add(build(date, knock1, dorm, "敲灯"));
                        User knock2 = pickNext(knockPool, pointers, knockKey, dayLeave, knock1.getId(),
                                sessionSelected, coolingIds, multiLimits, multiPicked, dailySelected, recalledSet);
                        if (knock2 != null) {
                            schedules.add(build(date, knock2, dorm, "敲灯"));
                        } else {
                            warnings.add(date + " " + dorm.getName() + " 敲灯第2人无人可用");
                        }
                    } else {
                        warnings.add(date + " " + dorm.getName() + " 敲灯无人可用");
                    }
                }
            }

            // 本周排班完成：写入冷却表（多排用户不进冷却表）
            for (Long uid : sessionSelected) {
                if (recalledSet.contains(uid)) {
                    // 回捞的人本就在冷却表，周次更新为本周（重新冷却）
                    dutyCoolingMapper.updateWeek(uid, week);
                } else {
                    DutyCooling c = new DutyCooling();
                    c.setUserId(uid);
                    c.setWeekNumber(week);
                    coolingInsert.add(c);
                }
            }
            for (int i = 0; i < coolingInsert.size(); i += BATCH_SIZE) {
                List<DutyCooling> sub = coolingInsert.subList(i, Math.min(i + BATCH_SIZE, coolingInsert.size()));
                dutyCoolingMapper.batchInsert(sub);
            }
        }

        // 保存指针（跨周持续）
        savePointers(pointers);

        // 保存：只删除本次实际生成排班的天（并集范围内被规则跳过的旧数据不动，避免误删）
        clearAndSave(schedules, allDormitories, dormitoryIds);
        AutoScheduleResult result = new AutoScheduleResult();
        result.setTotalSchedules(schedules.size());
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 从池中取下一个可用的人（三级）：
     * 1) 多排优先（当天一人一次，不限制一周多次，不进冷却表）；
     * 2) 正常选（排除冷却表/当天请假/本周已选/当天已选）；
     * 3) 人不够回捞冷却表（按指针轮流，回捞的人周次更新为本周）。
     */
    private User pickNext(List<User> pool, Map<String, Long> pointers, String poolKey,
                          Set<Long> dayLeave, Long excludeUserId, Set<Long> sessionSelected,
                          Set<Long> coolingIds, Map<Long, Integer> multiLimits, Map<Long, Integer> multiPicked,
                          Set<Long> dailySelected, Set<Long> recalledSet) {
        if (pool.isEmpty()) return null;
        User m = pickMulti(pool, pointers, poolKey, multiLimits, multiPicked, dayLeave, excludeUserId, dailySelected);
        if (m != null) { dailySelected.add(m.getId()); return m; }
        User p = pickOne(pool, pointers, poolKey, dayLeave, excludeUserId, sessionSelected, coolingIds, dailySelected);
        if (p != null) { sessionSelected.add(p.getId()); dailySelected.add(p.getId()); return p; }
        User r = pickRecalled(pool, pointers, poolKey, dayLeave, excludeUserId, dailySelected, coolingIds, recalledSet);
        if (r != null) {
            sessionSelected.add(r.getId());
            dailySelected.add(r.getId());
            recalledSet.add(r.getId());
            return r;
        }
        return null;
    }

    /** 多排优先：在池中按指针顺序找"申请了多排且覆盖本周"的人（当天不请假、当天未排，每周不超上限） */
    private User pickMulti(List<User> pool, Map<String, Long> pointers, String poolKey,
                           Map<Long, Integer> multiLimits, Map<Long, Integer> multiPicked,
                           Set<Long> dayLeave, Long excludeUserId, Set<Long> dailySelected) {
        if (multiLimits == null || multiLimits.isEmpty()) return null;
        int start = nextIndex(pool, pointers.get(poolKey));
        for (int k = 0; k < pool.size(); k++) {
            int idx = (start + k) % pool.size();
            User u = pool.get(idx);
            Integer limit = multiLimits.get(u.getId());
            if (limit == null) continue;
            if (dayLeave.contains(u.getId())) continue;
            if (excludeUserId != null && u.getId().equals(excludeUserId)) continue;
            if (dailySelected.contains(u.getId())) continue;
            int picked = multiPicked.getOrDefault(u.getId(), 0);
            if (picked >= limit) continue;
            multiPicked.put(u.getId(), picked + 1);
            pointers.put(poolKey, u.getId());
            return u;
        }
        return null;
    }

    /** 正常选：指针循环，排除冷却表(上周)/当天请假/本周已选/当天已选 */
    private User pickOne(List<User> pool, Map<String, Long> pointers, String poolKey,
                         Set<Long> dayLeave, Long excludeUserId, Set<Long> sessionSelected,
                         Set<Long> coolingIds, Set<Long> dailySelected) {
        int start = nextIndex(pool, pointers.get(poolKey));
        for (int k = 0; k < pool.size(); k++) {
            int idx = (start + k) % pool.size();
            User u = pool.get(idx);
            if (dayLeave.contains(u.getId())) continue;
            if (excludeUserId != null && u.getId().equals(excludeUserId)) continue;
            if (sessionSelected.contains(u.getId())) continue;
            if (dailySelected.contains(u.getId())) continue;
            if (coolingIds != null && coolingIds.contains(u.getId())) continue;
            pointers.put(poolKey, u.getId());
            return u;
        }
        return null;
    }

    /** 回捞：人不够时从冷却表里按指针轮流捞（当天不请假、当天未排） */
    private User pickRecalled(List<User> pool, Map<String, Long> pointers, String poolKey,
                              Set<Long> dayLeave, Long excludeUserId, Set<Long> dailySelected,
                              Set<Long> coolingIds, Set<Long> recalledSet) {
        if (coolingIds == null || coolingIds.isEmpty()) return null;
        int start = nextIndex(pool, pointers.get(poolKey));
        for (int k = 0; k < pool.size(); k++) {
            int idx = (start + k) % pool.size();
            User u = pool.get(idx);
            if (!coolingIds.contains(u.getId())) continue;
            if (dayLeave.contains(u.getId())) continue;
            if (excludeUserId != null && u.getId().equals(excludeUserId)) continue;
            if (dailySelected.contains(u.getId())) continue;
            pointers.put(poolKey, u.getId());
            return u;
        }
        return null;
    }

    /** 冷却表里"要排除/可回捞"的人：值班周 >= 本周-1（删除后通常只剩上周） */
    private Set<Long> loadCoolingIds(int minWeek) {
        Set<Long> set = new HashSet<>();
        for (DutyCooling c : dutyCoolingMapper.selectList(new LambdaQueryWrapper<DutyCooling>()
                .ge(DutyCooling::getWeekNumber, minWeek))) {
            set.add(c.getUserId());
        }
        return set;
    }

    /**
     * 宿舍多排：已批准且覆盖本周的用户 -> 每周可排天数上限。
     * 单周申请（weekStart==weekEnd）= 2天/周；跨周申请（weekStart<weekEnd）= 1天/周；同一用户取宽松值。
     */
    private Map<Long, Integer> loadMultiLimits(int week) {
        Map<Long, Integer> map = new HashMap<>();
        for (MultiDutyRequest m : multiDutyRequestMapper.selectList(new LambdaQueryWrapper<MultiDutyRequest>()
                .eq(MultiDutyRequest::getType, TYPE)
                .eq(MultiDutyRequest::getStatus, "approved")
                .le(MultiDutyRequest::getWeekStart, week)
                .ge(MultiDutyRequest::getWeekEnd, week))) {
            boolean multiWeek = m.getWeekEnd() != null && m.getWeekStart() != null && m.getWeekEnd() > m.getWeekStart();
            map.merge(m.getUserId(), multiWeek ? 1 : 2, Math::max);
        }
        return map;
    }

    /** 按学期开始日推算 date 所在周次（第1/2/3…周；学期开始前按第1周） */
    private int weekOf(LocalDate date, LocalDate semesterStart) {
        if (semesterStart == null) return 1;
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        long week = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
        return week < 1 ? 1 : (int) week;
    }

    /** 指针位置：上次排到的用户的下一位置；无记录或不在池中则从表头开始 */
    private int nextIndex(List<User> pool, Long currentUserId) {
        if (currentUserId == null) return 0;
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).getId().equals(currentUserId)) return (i + 1) % pool.size();
        }
        return 0;
    }

    private Map<String, Long> loadPointers() {
        Map<String, Long> map = new HashMap<>();
        for (SchedulePointer p : schedulePointerMapper.selectList(null)) {
            if (p.getCurrentUserId() != null) map.put(p.getPoolKey(), p.getCurrentUserId());
        }
        return map;
    }

    private void savePointers(Map<String, Long> pointers) {
        for (Map.Entry<String, Long> e : pointers.entrySet()) {
            schedulePointerMapper.upsert(e.getKey(), e.getValue());
        }
    }

    private List<User> sorted(List<User> list) {
        list.sort(Comparator.comparing(User::getId));
        return list;
    }

    private List<Dormitory> loadDormitories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return dormitoryService.list(new LambdaQueryWrapper<Dormitory>().eq(Dormitory::getStatus, 1));
        return dormitoryService.listByIds(ids);
    }

    private List<User> loadAllStaff() {
        return userService.list(new LambdaQueryWrapper<User>().eq(User::getStatus, 1).eq(User::getRole, "user"));
    }

    // 按天索引的请假用户（所有类型已审批，请假当天排除）
    private Map<LocalDate, Set<Long>> loadLeaveByDate(LocalDate start, LocalDate end) {
        List<LeaveRequest> leaves = leaveRequestService.list(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStatus, "approved")
                .le(LeaveRequest::getStartDate, end)
                .ge(LeaveRequest::getEndDate, start));
        Map<LocalDate, Set<Long>> map = new HashMap<>();
        for (LeaveRequest l : leaves) {
            if (l.getStartDate() == null) continue;
            LocalDate ldEnd = l.getEndDate() != null ? l.getEndDate() : l.getStartDate();
            for (LocalDate d = l.getStartDate(); !d.isAfter(ldEnd); d = d.plusDays(1)) {
                if (d.isBefore(start) || d.isAfter(end)) continue;
                map.computeIfAbsent(d, k -> new HashSet<>()).add(l.getUserId());
            }
        }
        return map;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY;
    }

    private DutySchedule build(LocalDate date, User user, Dormitory dorm, String slot) {
        DutySchedule ds = new DutySchedule();
        ds.setType(TYPE);
        ds.setLocationId(String.valueOf(dorm.getId()));
        ds.setLocationName(dorm.getName());
        ds.setUserId(user.getId());
        ds.setUserName(user.getRealName());
        ds.setDutyDate(date);
        ds.setTimeSlot(slot);
        return ds;
    }

    private void clearAndSave(List<DutySchedule> schedules, List<Dormitory> dorms, List<Long> dormitoryIds) {
        // 按地点分组本次生成的天，只删除这些天（范围外的旧数据不动，避免误删）
        Map<String, Set<LocalDate>> datesByLoc = new HashMap<>();
        for (DutySchedule ds : schedules) {
            datesByLoc.computeIfAbsent(ds.getLocationId(), k -> new HashSet<>()).add(ds.getDutyDate());
        }
        for (Map.Entry<String, Set<LocalDate>> e : datesByLoc.entrySet()) {
            dutyScheduleMapper.delete(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, TYPE)
                .eq(DutySchedule::getLocationId, e.getKey())
                .in(DutySchedule::getDutyDate, e.getValue()));
        }
        for (DutySchedule ds : schedules) dutyScheduleMapper.insert(ds);
    }
}
