package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.entity.Message;
import com.Firefire.paiban.entity.SwapRequest;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.service.DutyScheduleService;
import com.Firefire.paiban.service.MessageService;
import com.Firefire.paiban.service.SwapRequestService;
import com.Firefire.paiban.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/swap-requests")
@RequiredArgsConstructor
public class SwapRequestController {

    private final SwapRequestService swapRequestService;
    private final MessageService messageService;
    private final DutyScheduleService dutyScheduleService;
    private final UserService userService;

    @PostMapping
    public Result<Void> submit(@RequestBody SwapRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (req.getType() == null || req.getWeekNumber() == null || req.getDutyDate() == null
                || req.getTargetDay() == null || req.getTargetSlot() == null) {
            return Result.error("请填写完整的换班信息（目标时段必填）");
        }
        // 校验：只能申请换自己的班次（该用户在该类型+日期+时段确实有排班）
        Long owned = dutyScheduleService.count(new LambdaQueryWrapper<DutySchedule>()
            .eq(DutySchedule::getUserId, userId)
            .eq(DutySchedule::getType, req.getType())
            .eq(DutySchedule::getDutyDate, req.getDutyDate())
            .eq(DutySchedule::getTimeSlot, req.getTimeSlot()));
        if (owned == null || owned == 0) {
            return Result.error("只能申请换自己的班次");
        }
        req.setId(null);
        req.setUserId(userId);
        // 姓名字段填真实姓名，账号不能当姓名显示；查不到才退回登录账号
        User user = userService.getById(userId);
        req.setUserName(user != null && user.getRealName() != null && !user.getRealName().isBlank()
            ? user.getRealName() : (String) request.getAttribute("username"));
        req.setStatus("pending");
        req.setHandledBy(null);
        req.setHandledAt(null);
        swapRequestService.save(req);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<SwapRequest>> myRequests(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(swapRequestService.list(new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getUserId, userId)
                .orderByDesc(SwapRequest::getId)));
    }

    @GetMapping
    public Result<List<SwapRequest>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String type,
                                          HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        LambdaQueryWrapper<SwapRequest> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(SwapRequest::getStatus, status);
        if (type != null) w.eq(SwapRequest::getType, type);
        w.orderByAsc(SwapRequest::getStatus).orderByDesc(SwapRequest::getId);
        return Result.success(swapRequestService.list(w));
    }

    @PostMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        SwapRequest req = swapRequestService.getById(id);
        if (req == null) return Result.error("请求不存在");
        req.setStatus("done");
        req.setHandledBy((Long) request.getAttribute("userId"));
        req.setHandledAt(LocalDateTime.now());
        swapRequestService.updateById(req);
        return Result.success();
    }

    /** 同意/拒绝换班申请，并通过消息模块通知申请人 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam String action,
                                HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        SwapRequest req = swapRequestService.getById(id);
        if (req == null) return Result.error("申请不存在");
        if (!"approved".equals(action) && !"rejected".equals(action)) {
            return Result.error("无效操作");
        }
        if (!"pending".equals(req.getStatus())) {
            return Result.error("该申请已处理");
        }
        req.setStatus(action);
        req.setHandledBy((Long) request.getAttribute("userId"));
        req.setHandledAt(LocalDateTime.now());
        swapRequestService.updateById(req);

        notifyApplicant(req, action, request);
        return Result.success();
    }

    /** 通过系统消息通知申请人审批结果（通知失败不影响审批） */
    private void notifyApplicant(SwapRequest req, String action, HttpServletRequest request) {
        try {
            boolean approved = "approved".equals(action);
            Message msg = new Message();
            msg.setTitle("换班申请" + (approved ? "已同意" : "已拒绝"));
            msg.setType("approval");
            msg.setContent("您的换班申请（第" + req.getWeekNumber() + "周 " + req.getDutyDate()
                + " " + req.getTimeSlot() + " → " + req.getTargetDay() + " " + req.getTargetSlot()
                + "）已被管理员" + (approved ? "同意" : "拒绝") + "。"
                + (approved ? "如需实际换班，请留意排班表是否已由管理员调整。" : ""));
            // 发送人显示真实姓名，账号不能当姓名显示
            Long adminId = (Long) request.getAttribute("userId");
            User admin = userService.getById(adminId);
            msg.setSenderName(admin != null && admin.getRealName() != null && !admin.getRealName().isBlank()
                ? admin.getRealName() : (String) request.getAttribute("username"));
            msg.setSenderId(adminId);
            msg.setReceivers(String.valueOf(req.getUserId()));
            msg.setStatus(1);
            messageService.save(msg);
        } catch (Exception ignored) {
            // 通知失败不影响审批结果
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SwapRequest req = swapRequestService.getById(id);
        if (req == null) return Result.error("请求不存在");
        Long userId = (Long) request.getAttribute("userId");
        if (!"admin".equals(request.getAttribute("role"))) {
            // 普通用户只能撤销自己的、且仍为待处理的申请
            if (!userId.equals(req.getUserId())) return Result.error("无权限");
            if (!"pending".equals(req.getStatus())) return Result.error("该申请已处理，无法撤销");
        }
        swapRequestService.removeById(id);
        return Result.success();
    }
}
