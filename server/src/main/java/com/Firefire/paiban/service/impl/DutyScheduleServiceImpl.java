package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.dto.AutoScheduleResult;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.entity.LeaveRequest;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.Firefire.paiban.scheduling.SchedulingStrategy;
import com.Firefire.paiban.service.DutyScheduleService;
import com.Firefire.paiban.service.LeaveRequestService;
import com.Firefire.paiban.service.HolidayService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DutyScheduleServiceImpl extends ServiceImpl<DutyScheduleMapper, DutySchedule> implements DutyScheduleService {

    private final Map<String, SchedulingStrategy> strategyMap = new HashMap<>();
    private final LeaveRequestService leaveRequestService;
    private final HolidayService holidayService;

    public DutyScheduleServiceImpl(List<SchedulingStrategy> strategies,
                                   LeaveRequestService leaveRequestService,
                                   HolidayService holidayService) {
        for (SchedulingStrategy strategy : strategies) {
            strategyMap.put(strategy.getType(), strategy);
        }
        this.leaveRequestService = leaveRequestService;
        this.holidayService = holidayService;
    }

    @Override
    public void autoSchedule(String type, String locationId, String locationName, Long[] userIds, String[] userNames, String startDateStr, String endDateStr, String timeSlot) {
        // 只删除当前日期范围内的记录，保留其他周次的历史排班
        remove(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, type)
                .eq(DutySchedule::getLocationId, locationId)
                .ge(DutySchedule::getDutyDate, startDateStr)
                .le(DutySchedule::getDutyDate, endDateStr));

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        Set<Long> leaveUserIds = leaveRequestService.list(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStatus, "approved")
                .le(LeaveRequest::getStartDate, endDate)
                .ge(LeaveRequest::getEndDate, startDate))
                .stream().map(LeaveRequest::getUserId).collect(Collectors.toSet());

        String[] allTimeSlots;
        if ("dormitory".equals(type)) {
            allTimeSlots = new String[]{timeSlot != null ? timeSlot : "巡班"};
        } else {
            allTimeSlots = new String[]{"1-2 节", "3-4 节", "5-6 节", "7-8 节"};
        }

        List<Integer> availableIndices = new ArrayList<>();
        for (int i = 0; i < userIds.length; i++) {
            if (!leaveUserIds.contains(userIds[i])) {
                availableIndices.add(i);
            }
        }
        if (availableIndices.isEmpty()) {
            throw new RuntimeException("所有人员都在请假中，无法排班");
        }
        int availCount = availableIndices.size();

        for (String slot : allTimeSlots) {
            List<Integer> shuffled = new ArrayList<>(availableIndices);
            Collections.shuffle(shuffled);

            int index = 0;
            for (int i = 0; i <= ChronoUnit.DAYS.between(startDate, endDate); i++) {
                LocalDate currentDate = startDate.plusDays(i);
                if (currentDate.getDayOfWeek().getValue() >= 6
                        || holidayService.isHoliday(currentDate)) {
                    continue;
                }
                int idx = shuffled.get(index % availCount);
                DutySchedule schedule = new DutySchedule();
                schedule.setType(type);
                schedule.setLocationId(locationId);
                schedule.setLocationName(locationName);
                schedule.setUserId(userIds[idx]);
                schedule.setUserName(userNames[idx]);
                schedule.setDutyDate(currentDate);
                schedule.setTimeSlot(slot);
                save(schedule);
                index++;
            }
        }
    }

    @Override
    public AutoScheduleResult autoScheduleDormitory(Map<String, Object> params) {
        SchedulingStrategy strategy = strategyMap.get("dormitory");
        if (strategy == null) {
            throw new RuntimeException("未找到宿舍排班策略");
        }
        return strategy.generate(params);
    }

    @Override
    public AutoScheduleResult autoScheduleOffice(Map<String, Object> params) {
        SchedulingStrategy strategy = strategyMap.get("office");
        if (strategy == null) {
            throw new RuntimeException("未找到办公室排班策略");
        }
        return strategy.generate(params);
    }
}
