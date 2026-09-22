package com.Firefire.paiban.scheduling;

import com.Firefire.paiban.dto.AutoScheduleResult;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.mapper.DutyAdjustmentMakeupMapper;
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
 * 办公室排班（指针循环 + 冷却/多排/回捞方案）：
 * 所有用户一张池（按用户ID顺序），一根指针从表头往下排、排完一圈回表头；
 * 指针持久化到 schedule_pointer 表，跨周继续。
 * 每节课取 4 人，选人分三级：
 *   1) 多排优先：申请了"办公室多排"且已批准、覆盖本周、该时段有空闲的人优先选
 *      （当天一人一次，不限制一周多次，不进冷却表）；
 *   2) 正常选：指针循环，排除冷却表（上周排过，防两周连排）+ 当天请假 + 该时段没空闲 + 本周已选；
 *   3) 回捞：人不够时从冷却表按指针轮流捞该时段有空闲的人（回捞的人周次更新为本周）。
 * 冷却表（duty_cooling）记录最近两周排过的人，隔一周删除冷却结束的记录；
 * 排班按周处理：每周结束把本周排的普通人写入冷却表。
 * 周末仅补班日排班，空闲按补课日（replaced_date）的星期+单双周计算。
 */
@Component
@RequiredArgsConstructor
public class OfficeSchedulingStrategy implements SchedulingStrategy {

    private final OfficeService officeService;
    private final UserService userService;
    private final DutyScheduleMapper dutyScheduleMapper;
    private final LeaveRequestService leaveRequestService;
    private final HolidayService holidayService;
    private final TimeSlotService timeSlotService;
    private final UserAvailabilityService userAvailabilityService;
    private final SchedulePointerMapper schedulePointerMapper;
    private final DutyAdjustmentMapper dutyAdjustmentMapper;
    private final DutyAdjustmentMakeupMapper dutyAdjustmentMakeupMapper;
    private final SemesterConfigService semesterConfigService;
    private final DutyCoolingMapper dutyCoolingMapper;
    private final MultiDutyRequestMapper multiDutyRequestMapper;
    private final OfficeScheduleConfigService officeScheduleConfigService;

    private static final String TYPE = "office";
    private static final String POOL_OFFICE = "office";
    /** 冷却表批量插入每批条数 */
    private static final int BATCH_SIZE = 200;

    @Override
    public String getType() { return TYPE; }

    @Override
    @SuppressWarnings("unchecked")
    public AutoScheduleResult generate(Map<String, Object> params) {
        List<Long> officeIds = (List<Long>) params.get("officeIds");
        String startDateStr = (String) params.get("startDate");
        String endDateStr = (String) params.get("endDate");

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        List<Office> offices = loadOffices(officeIds);
        // 所有用户按ID顺序（用户表顺序）作为指针池
        List<User> officePool = sorted(loadAllStaff());
        // 用按 label 去重后的时段列表
        List<TimeSlot> timeSlots = timeSlotService.list();

        // 学期开始日（用于按天算单双周/周次）
        LocalDate semesterStart = null;
        SemesterConfig cfg = semesterConfigService.getCurrent();
        if (cfg != null && cfg.getStartDate() != null) semesterStart = cfg.getStartDate();
        if (semesterStart == null && params.get("semesterStart") != null) {
            semesterStart = LocalDate.parse((String) params.get("semesterStart"));
        }

        // 调休补课映射：补班日 -> 补哪天的课（空闲按 replaced_date 的星期+单双周计算）
        Map<LocalDate, LocalDate> makeupMap = new HashMap<>();
        for (DutyAdjustmentMakeup mk : dutyAdjustmentMakeupMapper.selectList(null)) {
            if (mk.getMakeupDate() != null && mk.getReplacedDate() != null) makeupMap.put(mk.getMakeupDate(), mk.getReplacedDate());
        }
        // 循环起点扩展到"与本周[startDate,endDate]有交集"的调休开始日（补班日在其调休周期内，一并覆盖）
        LocalDate loopStart = startDate;
        for (DutyAdjustment adj : dutyAdjustmentMapper.selectList(null)) {
            if (adj.getStartDate() != null && adj.getEndDate() != null
                    && !adj.getStartDate().isAfter(endDate) && !adj.getEndDate().isBefore(startDate)
                    && adj.getStartDate().isBefore(loopStart)) {
                loopStart = adj.getStartDate();
            }
        }

        // 空闲时间：userId -> "单双周-星期-时段id" 集合（保留单双周，按天匹配）
        Map<Long, Set<String>> availMap = loadAvailability(officePool);
        // 按天请假用户
        Map<LocalDate, Set<Long>> leaveByDate = loadLeaveByDate(startDate, endDate);

        // 读取办公室指针（跨周持续）
        Map<String, Long> pointers = loadPointers();

        // 按周次分组：冷却按周维护、一周一人一次按周重置
        TreeMap<Integer, List<LocalDate>> byWeek = new TreeMap<>();
        for (LocalDate d = loopStart; !d.isAfter(endDate); d = d.plusDays(1)) {
            byWeek.computeIfAbsent(weekOf(d, semesterStart), k -> new ArrayList<>()).add(d);
        }

        List<DutySchedule> schedules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 每节课值班人数（可配置，默认 4）
        int capacity = officeScheduleConfigService.getCapacity();
        for (Map.Entry<Integer, List<LocalDate>> weekEntry : byWeek.entrySet()) {
            int week = weekEntry.getKey();
            // 清理冷却已结束的记录（本周-1 之前）
            dutyCoolingMapper.deleteBeforeWeek(week - 1);
            // 冷却排除：本周-1 及之后的（删除后只剩上周，防两周连排）
            Set<Long> coolingIds = loadCoolingIds(week - 1);
            // 多排：办公室类型、已批准、覆盖本周 -> 每人每周可排天数上限（单周2天/跨周1天）
            Map<Long, Integer> multiLimits = loadMultiLimits(week);
            // 本周多排用户已排天数（达到上限后本周不再排；新一周清零重算）
            Map<Long, Integer> multiPicked = new HashMap<>();
            // 本周已选普通人（一周一人只排一次；多排用户豁免）
            Set<Long> sessionSelected = new HashSet<>();
            // 本周回捞的人（本就在冷却表，周次更新为本周）
            Set<Long> recalledSet = new HashSet<>();
            // 本周要写入冷却表的人（普通新排）
            List<DutyCooling> coolingInsert = new ArrayList<>();

            for (LocalDate date : weekEntry.getValue()) {
                int dow = date.getDayOfWeek().getValue();
                LocalDate replaced = makeupMap.get(date);
                int effectiveDow;
                String effectiveParity;
                if (dow == DayOfWeek.SATURDAY.getValue() || dow == DayOfWeek.SUNDAY.getValue()) {
                    // 周末：仅补班日排班，空闲按"补的那天"的星期+单双周
                    if (replaced == null) continue;
                    effectiveDow = replaced.getDayOfWeek().getValue();
                    effectiveParity = weekParity(replaced, semesterStart);
                } else {
                    // 工作日：放假日跳过（办公室白天放假不值班），否则按当天单双周
                    if (holidayService.isHoliday(date)) continue;
                    effectiveDow = dow;
                    effectiveParity = weekParity(date, semesterStart);
                }
                Set<Long> dayLeave = leaveByDate.getOrDefault(date, Collections.emptySet());
                // 当天已排（多排用户也遵守当天一人一次）
                Set<Long> dailySelected = new HashSet<>();
                for (Office office : offices) {
                    for (TimeSlot slot : timeSlots) {
                        String needKey = effectiveParity + "-" + effectiveDow + "-" + slot.getId();
                        List<User> chosen = pickN(officePool, pointers, POOL_OFFICE, dayLeave, needKey, availMap,
                                capacity, sessionSelected, coolingIds, multiLimits, multiPicked, dailySelected, recalledSet);
                        for (User u : chosen) {
                            DutySchedule ds = new DutySchedule();
                            ds.setType(TYPE);
                            ds.setLocationId(String.valueOf(office.getId()));
                            ds.setLocationName(office.getName());
                            ds.setUserId(u.getId());
                            ds.setUserName(u.getRealName());
                            ds.setDutyDate(date);
                            ds.setTimeSlot(slot.getLabel());
                            schedules.add(ds);
                        }
                        if (chosen.size() < capacity) {
                            warnings.add(date + " " + office.getName() + " " + slot.getLabel()
                                + " 人员不足，仅安排 " + chosen.size() + " 人");
                        }
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

        clearAndSave(schedules, offices);
        AutoScheduleResult result = new AutoScheduleResult();
        result.setTotalSchedules(schedules.size());
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 从池中按指针取最多 n 个"当天不请假 + 该时段有空闲"的人（三级）：
     * 1) 多排优先（当天一人一次，每周不超上限[单周2天/跨周1天]，不进冷却表）；
     * 2) 正常选（排除冷却表/当天请假/本周已选/当天已选）；
     * 3) 人不够回捞冷却表（按指针轮流，回捞的人周次更新为本周）。
     */
    private List<User> pickN(List<User> pool, Map<String, Long> pointers, String poolKey,
                             Set<Long> dayLeave, String needKey, Map<Long, Set<String>> availMap, int n,
                             Set<Long> sessionSelected, Set<Long> coolingIds, Map<Long, Integer> multiLimits,
                             Map<Long, Integer> multiPicked, Set<Long> dailySelected, Set<Long> recalledSet) {
        List<User> result = new ArrayList<>();
        if (pool.isEmpty()) return result;
        collectN(pool, pointers, poolKey, dayLeave, needKey, availMap, n, sessionSelected,
                coolingIds, multiLimits, multiPicked, dailySelected, recalledSet, result);
        if (!result.isEmpty()) pointers.put(poolKey, result.get(result.size() - 1).getId());
        return result;
    }

    private void collectN(List<User> pool, Map<String, Long> pointers, String poolKey,
                          Set<Long> dayLeave, String needKey, Map<Long, Set<String>> availMap, int limit,
                          Set<Long> sessionSelected, Set<Long> coolingIds, Map<Long, Integer> multiLimits,
                          Map<Long, Integer> multiPicked, Set<Long> dailySelected, Set<Long> recalledSet,
                          List<User> result) {
        if (limit <= 0) return;
        int start = nextIndex(pool, pointers.get(poolKey));
        // 第一遍：多排优先（当天一人一次，每周不超上限，不进冷却表）
        for (int k = 0; k < pool.size() && result.size() < limit; k++) {
            int idx = (start + k) % pool.size();
            User u = pool.get(idx);
            Integer limitOfUser = multiLimits.get(u.getId());
            if (limitOfUser == null) continue;
            if (dayLeave.contains(u.getId())) continue;
            if (!hasAvail(u.getId(), needKey, availMap)) continue;
            if (dailySelected.contains(u.getId())) continue;
            int picked = multiPicked.getOrDefault(u.getId(), 0);
            if (picked >= limitOfUser) continue;
            result.add(u);
            multiPicked.put(u.getId(), picked + 1);
            dailySelected.add(u.getId());
        }
        // 第二遍：正常选（排除冷却/请假/本周已选/当天已选）
        for (int k = 0; k < pool.size() && result.size() < limit; k++) {
            int idx = (start + k) % pool.size();
            User u = pool.get(idx);
            if (multiLimits.containsKey(u.getId())) continue;
            if (dayLeave.contains(u.getId())) continue;
            if (!hasAvail(u.getId(), needKey, availMap)) continue;
            if (dailySelected.contains(u.getId())) continue;
            if (sessionSelected.contains(u.getId())) continue;
            if (coolingIds.contains(u.getId())) continue;
            result.add(u);
            sessionSelected.add(u.getId());
            dailySelected.add(u.getId());
        }
        // 第三遍：回捞冷却表（人不够时按指针轮流，回捞的人周次更新为本周）
        if (result.size() < limit) {
            for (int k = 0; k < pool.size() && result.size() < limit; k++) {
                int idx = (start + k) % pool.size();
                User u = pool.get(idx);
                if (!coolingIds.contains(u.getId())) continue;
                if (dayLeave.contains(u.getId())) continue;
                if (!hasAvail(u.getId(), needKey, availMap)) continue;
                if (dailySelected.contains(u.getId())) continue;
                result.add(u);
                sessionSelected.add(u.getId());
                dailySelected.add(u.getId());
                recalledSet.add(u.getId());
            }
        }
    }

    private boolean hasAvail(Long uid, String needKey, Map<Long, Set<String>> availMap) {
        Set<String> avail = availMap.get(uid);
        return avail != null && avail.contains(needKey);
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
     * 办公室多排：已批准且覆盖本周的用户 -> 每周可排天数上限。
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

    private List<User> sorted(List<User> list) {
        list.sort(Comparator.comparing(User::getId));
        return list;
    }

    private List<Office> loadOffices(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return officeService.list(new LambdaQueryWrapper<Office>().eq(Office::getStatus, 1));
        return officeService.listByIds(ids);
    }

    private List<User> loadAllStaff() {
        return userService.list(new LambdaQueryWrapper<User>().eq(User::getStatus, 1).eq(User::getRole, "user"));
    }

    // 空闲时间：userId -> "单双周-星期-时段id" 集合（保留单双周，排班时按天匹配）
    private Map<Long, Set<String>> loadAvailability(List<User> staff) {
        Map<Long, Set<String>> map = new HashMap<>();
        for (User u : staff) {
            Set<String> slots = new HashSet<>();
            for (UserAvailability ua : userAvailabilityService.getByUserId(u.getId())) {
                if (ua.getWeekParity() == null) continue;
                slots.add(ua.getWeekParity() + "-" + ua.getDayOfWeek() + "-" + ua.getTimeSlotId());
            }
            map.put(u.getId(), slots);
        }
        return map;
    }

    /** 按学期开始日推算某天所在周的单双周（第1、3、5…周=单周 odd；第2、4…周=双周 even） */
    private String weekParity(LocalDate date, LocalDate semesterStart) {
        if (semesterStart == null) return "both";
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        long week = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
        return (week % 2 == 1) ? "odd" : "even";
    }

    /** 按学期开始日推算 date 所在周次（第1/2/3…周；学期开始前按第1周） */
    private int weekOf(LocalDate date, LocalDate semesterStart) {
        if (semesterStart == null) return 1;
        LocalDate anchor = semesterStart.minusDays(semesterStart.getDayOfWeek().getValue() - 1);
        long week = ChronoUnit.DAYS.between(anchor, date) / 7 + 1;
        return week < 1 ? 1 : (int) week;
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

    private void clearAndSave(List<DutySchedule> schedules, List<Office> offices) {
        Set<String> locIds = offices.stream().map(o -> String.valueOf(o.getId())).collect(Collectors.toSet());
        Map<String, LocalDate> idMin = new HashMap<>();
        Map<String, LocalDate> idMax = new HashMap<>();
        for (DutySchedule ds : schedules) {
            String locId = ds.getLocationId();
            idMin.merge(locId, ds.getDutyDate(), (a, b) -> a.isBefore(b) ? a : b);
            idMax.merge(locId, ds.getDutyDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
        for (String locId : locIds) {
            LocalDate min = idMin.get(locId);
            if (min == null) continue;
            dutyScheduleMapper.delete(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, TYPE)
                .eq(DutySchedule::getLocationId, locId)
                .ge(DutySchedule::getDutyDate, min)
                .le(DutySchedule::getDutyDate, idMax.get(locId)));
        }
        for (DutySchedule ds : schedules) dutyScheduleMapper.insert(ds);
    }
}
