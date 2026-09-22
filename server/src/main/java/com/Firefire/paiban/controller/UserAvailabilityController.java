package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.UserAvailability;
import com.Firefire.paiban.service.UserAvailabilityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/availabilities")
@RequiredArgsConstructor
public class UserAvailabilityController {

    private final UserAvailabilityService userAvailabilityService;

    @GetMapping("/my")
    public Result<List<UserAvailability>> getMyAvailabilities(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userAvailabilityService.getByUserId(userId));
    }

    @GetMapping("/user/{userId}")
    public Result<List<UserAvailability>> getByUserId(@PathVariable Long userId) {
        return Result.success(userAvailabilityService.getByUserId(userId));
    }

    @GetMapping("/all")
    public Result<List<Map<String, Object>>> getAll() {
        return Result.success(userAvailabilityService.getAllWithUser());
    }

    @PostMapping("/batch")
    public Result<Void> batchSave(@RequestBody List<UserAvailability> availabilities, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userAvailabilityService.batchSave(userId, availabilities);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            UserAvailability availability = userAvailabilityService.getById(id);
            if (availability == null) {
                return Result.error("记录不存在");
            }
            // 管理员可删任意记录；普通用户只能删自己名下的
            if (!"admin".equals(request.getAttribute("role"))) {
                Long userId = (Long) request.getAttribute("userId");
                if (!Objects.equals(availability.getUserId(), userId)) {
                    return Result.error("无权限");
                }
            }
            boolean removed = userAvailabilityService.removeById(id);
            if (removed) {
                return Result.success();
            } else {
                return Result.error("记录不存在");
            }
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
