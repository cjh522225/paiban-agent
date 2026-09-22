package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.service.OfficeScheduleConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/office-schedule-config")
@RequiredArgsConstructor
public class OfficeScheduleConfigController {

    private final OfficeScheduleConfigService officeScheduleConfigService;

    @GetMapping
    public Result<Map<String, Integer>> get(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        return Result.success(Map.of("slotCapacity", officeScheduleConfigService.getCapacity()));
    }

    @PutMapping
    public Result<Map<String, Integer>> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        Object raw = body.get("slotCapacity");
        int cap;
        try {
            cap = raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return Result.error("每节课人数必须为 1~20 的整数");
        }
        if (cap < 1 || cap > 20) return Result.error("每节课人数必须为 1~20 的整数");
        int saved = officeScheduleConfigService.saveCapacity(cap);
        return Result.success(Map.of("slotCapacity", saved));
    }
}
