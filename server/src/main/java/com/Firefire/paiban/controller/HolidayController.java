package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.Holiday;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.service.HolidayService;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;
    private final DutyScheduleMapper dutyScheduleMapper;

    @GetMapping
    public Result<List<Holiday>> list() {
        return Result.success(holidayService.list());
    }

    @PostMapping
    public Result<Void> save(@RequestBody Holiday holiday) {
        holidayService.save(holiday);

        LocalDate start = holiday.getStartDate();
        LocalDate end = holiday.getEndDate() != null ? holiday.getEndDate() : start;
        if (start != null) {
            dutyScheduleMapper.delete(new LambdaQueryWrapper<DutySchedule>()
                .ge(DutySchedule::getDutyDate, start)
                .le(DutySchedule::getDutyDate, end));
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        holidayService.removeById(id);
        return Result.success();
    }
}
