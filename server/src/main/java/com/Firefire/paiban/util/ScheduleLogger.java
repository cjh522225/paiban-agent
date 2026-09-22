package com.Firefire.paiban.util;

import com.Firefire.paiban.entity.DutySchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ScheduleLogger {

    private static final Logger log = LoggerFactory.getLogger(ScheduleLogger.class);

    public static void logQuery(String type, String locationId, String startDate, String endDate,
                                 List<DutySchedule> result) {
        String label = "dormitory".equals(type) ? "宿舍" : "办公室";
        String range = (startDate != null && endDate != null)
                ? startDate + " ~ " + endDate
                : "全部日期";

        if (result.isEmpty()) {
            log.info("[{}值班] locationId={}, 日期={} → 无排班数据", label, locationId, range);
            return;
        }

        String summary = result.stream()
                .map(s -> String.format("%s %s %s",
                        s.getDutyDate(), s.getTimeSlot(), s.getUserName()))
                .collect(Collectors.joining(", "));

        log.info("[{}值班] locationId={}, 日期={} → 共{}条: {}",
                label, locationId, range, result.size(), summary);
    }
}
