package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.MultiDutyRequest;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.service.MultiDutyRequestService;
import com.Firefire.paiban.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/multi-duty")
@RequiredArgsConstructor
public class MultiDutyController {

    private final MultiDutyRequestService multiDutyRequestService;
    private final UserService userService;

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> myRequests(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<MultiDutyRequest> list = multiDutyRequestService.list(
                new LambdaQueryWrapper<MultiDutyRequest>()
                        .eq(MultiDutyRequest::getUserId, userId)
                        .orderByDesc(MultiDutyRequest::getId));
        return Result.success(withUserInfo(list));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String type,
                                                  HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        LambdaQueryWrapper<MultiDutyRequest> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(MultiDutyRequest::getStatus, status);
        if (type != null) w.eq(MultiDutyRequest::getType, type);
        w.orderByDesc(MultiDutyRequest::getId);
        return Result.success(withUserInfo(multiDutyRequestService.list(w)));
    }

    @PostMapping
    public Result<Void> submit(@RequestBody MultiDutyRequest req, HttpServletRequest request) {
        boolean isAdmin = "admin".equals(request.getAttribute("role"));
        Long loginUserId = (Long) request.getAttribute("userId");
        if (req.getWeekStart() == null || req.getWeekEnd() == null) {
            return Result.error("请填写周次范围");
        }
        if (req.getWeekEnd() < req.getWeekStart()) {
            return Result.error("结束周次不能早于开始周次");
        }
        // 管理员代客申请：body 显式传了 userId 时记在被代客名下；普通用户始终记自己
        Long targetUserId = isAdmin && req.getUserId() != null ? req.getUserId() : loginUserId;
        if (isAdmin && req.getUserId() != null) {
            User target = userService.getById(targetUserId);
            if (target == null) return Result.error("代客申请的用户不存在");
            if (target.getStatus() == null || target.getStatus() != 1) return Result.error("代客申请的用户已禁用");
        }
        req.setId(null);
        req.setUserId(targetUserId);
        req.setStatus("pending");
        req.setApproverId(null);
        multiDutyRequestService.save(req);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam String action,
                                HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        MultiDutyRequest req = multiDutyRequestService.getById(id);
        if (req == null) return Result.error("申请不存在");
        if (!"approved".equals(action) && !"rejected".equals(action)) {
            return Result.error("无效操作");
        }
        req.setStatus(action);
        req.setApproverId((Long) request.getAttribute("userId"));
        multiDutyRequestService.updateById(req);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        MultiDutyRequest req = multiDutyRequestService.getById(id);
        if (req == null) return Result.error("申请不存在");
        Long userId = (Long) request.getAttribute("userId");
        if (!"admin".equals(request.getAttribute("role"))) {
            // 普通用户只能撤销自己的、且仍为待处理的申请
            if (!userId.equals(req.getUserId())) return Result.error("无权限");
            if (!"pending".equals(req.getStatus())) return Result.error("该申请已处理，无法撤销");
        }
        multiDutyRequestService.removeById(id);
        return Result.success();
    }

    private List<Map<String, Object>> withUserInfo(List<MultiDutyRequest> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MultiDutyRequest r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("type", r.getType());
            m.put("weekStart", r.getWeekStart());
            m.put("weekEnd", r.getWeekEnd());
            m.put("note", r.getNote());
            m.put("status", r.getStatus());
            m.put("approverId", r.getApproverId());
            m.put("createTime", r.getCreateTime());
            User u = userService.getById(r.getUserId());
            m.put("userName", u != null ? u.getRealName() : "");
            result.add(m);
        }
        return result;
    }
}
