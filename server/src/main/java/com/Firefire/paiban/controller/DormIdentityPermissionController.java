package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.DormIdentityPermission;
import com.Firefire.paiban.service.DormIdentityPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm-identity-permission")
@RequiredArgsConstructor
public class DormIdentityPermissionController {

    private final DormIdentityPermissionService dormIdentityPermissionService;

    @GetMapping
    public Result<List<DormIdentityPermission>> list(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        return Result.success(dormIdentityPermissionService.list());
    }

    @PutMapping
    public Result<Void> save(@RequestBody List<DormIdentityPermission> rows, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        dormIdentityPermissionService.saveMatrix(rows);
        return Result.success();
    }
}
