package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.Holiday;
import com.Firefire.paiban.mapper.HolidayMapper;
import com.Firefire.paiban.service.HolidayService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class HolidayServiceImpl extends ServiceImpl<HolidayMapper, Holiday> implements HolidayService {
    @Override
    public boolean isHoliday(LocalDate date) {
        return count(new LambdaQueryWrapper<Holiday>()
            .le(Holiday::getStartDate, date)
            .ge(Holiday::getEndDate, date)) > 0;
    }

    @Override
    public boolean isLastDayOfHoliday(LocalDate date) {
        return count(new LambdaQueryWrapper<Holiday>()
            .eq(Holiday::getEndDate, date)) > 0;
    }

    @Override
    public boolean isDayBeforeHoliday(LocalDate date) {
        return count(new LambdaQueryWrapper<Holiday>()
            .eq(Holiday::getStartDate, date.plusDays(1))) > 0;
    }

    @Override
    public List<Holiday> getActiveHolidays(LocalDate start, LocalDate end) {
        return list(new LambdaQueryWrapper<Holiday>()
            .le(Holiday::getStartDate, end)
            .ge(Holiday::getEndDate, start));
    }
}
