package com.Firefire.paiban.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.Firefire.paiban.entity.Holiday;
import java.time.LocalDate;
import java.util.List;

public interface HolidayService extends IService<Holiday> {
    boolean isHoliday(LocalDate date);
    /** 是否某放假的最后一天（学生返校那晚，宿舍要排班） */
    boolean isLastDayOfHoliday(LocalDate date);
    /** 是否某放假的前一天（学生离校那晚，宿舍不排班） */
    boolean isDayBeforeHoliday(LocalDate date);
    List<Holiday> getActiveHolidays(LocalDate start, LocalDate end);
}
