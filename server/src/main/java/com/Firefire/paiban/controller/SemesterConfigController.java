package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.SemesterConfig;
import com.Firefire.paiban.service.SemesterConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/semester")
@RequiredArgsConstructor
public class SemesterConfigController {

    private final SemesterConfigService semesterConfigService;

    @GetMapping
    public Result<SemesterConfig> get() {
        return Result.success(semesterConfigService.getCurrent());
    }

    @PostMapping
    public Result<Void> save(@RequestBody SemesterConfig config) {
        semesterConfigService.saveOrUpdate(config);
        return Result.success();
    }
}
