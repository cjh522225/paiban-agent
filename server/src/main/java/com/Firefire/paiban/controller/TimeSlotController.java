package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.TimeSlot;
import com.Firefire.paiban.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    @GetMapping
    public Result<List<TimeSlot>> list() {
        return Result.success(timeSlotService.list());
    }

    @GetMapping("/{id}")
    public Result<TimeSlot> getById(@PathVariable Long id) {
        return Result.success(timeSlotService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody TimeSlot timeSlot) {
        timeSlotService.save(timeSlot);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody TimeSlot timeSlot) {
        timeSlotService.updateById(timeSlot);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        timeSlotService.removeById(id);
        return Result.success();
    }
}
