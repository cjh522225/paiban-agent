package com.Firefire.paiban.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.Firefire.paiban.dto.AutoScheduleResult;
import com.Firefire.paiban.entity.DutySchedule;
import java.util.Map;

public interface DutyScheduleService extends IService<DutySchedule> {
    void autoSchedule(String type, String locationId, String locationName, Long[] userIds, String[] userNames, String startDate, String endDate, String timeSlot);
    AutoScheduleResult autoScheduleDormitory(Map<String, Object> params);
    AutoScheduleResult autoScheduleOffice(Map<String, Object> params);
}
