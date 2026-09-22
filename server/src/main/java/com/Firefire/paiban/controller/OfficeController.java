package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.Office;
import com.Firefire.paiban.service.OfficeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offices")
@RequiredArgsConstructor
public class OfficeController {

    private final OfficeService officeService;

    @GetMapping
    public Result<List<Office>> list(@RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Office> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Office::getStatus, status);
        }
        return Result.success(officeService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<Office> getById(@PathVariable Long id) {
        return Result.success(officeService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Office office) {
        officeService.save(office);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Office office) {
        officeService.updateById(office);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        officeService.removeById(id);
        return Result.success();
    }
}
