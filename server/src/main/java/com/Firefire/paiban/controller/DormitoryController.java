package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.Dormitory;
import com.Firefire.paiban.service.DormitoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dormitories")
@RequiredArgsConstructor
public class DormitoryController {

    private final DormitoryService dormitoryService;

    @GetMapping
    public Result<List<Dormitory>> list(@RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Dormitory> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Dormitory::getStatus, status);
        }
        return Result.success(dormitoryService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<Dormitory> getById(@PathVariable Long id) {
        return Result.success(dormitoryService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Dormitory dormitory) {
        dormitoryService.save(dormitory);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Dormitory dormitory) {
        dormitoryService.updateById(dormitory);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormitoryService.removeById(id);
        return Result.success();
    }
}
