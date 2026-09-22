package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.entity.LeaveRequest;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.service.DutyScheduleService;
import com.Firefire.paiban.service.LeaveRequestService;
import com.Firefire.paiban.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final DutyScheduleService dutyScheduleService;
    private final UserService userService;

    @GetMapping
    public Result<List<LeaveRequest>> list(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        if ("user".equals(role)) {
            wrapper.eq(LeaveRequest::getUserId, userId);
        }
        wrapper.orderByDesc(LeaveRequest::getCreateTime);
        return Result.success(leaveRequestService.list(wrapper));
    }

    @GetMapping("/pending")
    public Result<List<LeaveRequest>> getPendingList() {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaveRequest::getStatus, "pending");
        wrapper.orderByDesc(LeaveRequest::getCreateTime);
        return Result.success(leaveRequestService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<LeaveRequest> getById(@PathVariable Long id) {
        return Result.success(leaveRequestService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody LeaveRequest leaveRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        leaveRequest.setUserId(userId);
        // 姓名字段填真实姓名，账号不能当姓名显示；查不到才退回登录账号
        User user = userService.getById(userId);
        leaveRequest.setUserName(user != null && user.getRealName() != null && !user.getRealName().isBlank()
            ? user.getRealName() : (String) request.getAttribute("username"));
        leaveRequest.setStatus("pending");
        // 前端仅选择单天日期，end_date 未传时默认等于 start_date
        if (leaveRequest.getEndDate() == null) {
            leaveRequest.setEndDate(leaveRequest.getStartDate());
        }
        if (leaveRequest.getStartDate() == null || leaveRequest.getEndDate() == null) {
            return Result.error("请假日期不能为空");
        }
        // 结束日期必须在开始日期当天或之后
        if (leaveRequest.getEndDate().isBefore(leaveRequest.getStartDate())) {
            return Result.error("请假结束日期不能早于开始日期");
        }
        // 计算请假天数：结束日期 - 开始日期 + 1
        leaveRequest.setDays(Math.toIntExact(
            ChronoUnit.DAYS.between(leaveRequest.getStartDate(), leaveRequest.getEndDate()) + 1));
        leaveRequestService.save(leaveRequest);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody LeaveRequest leaveRequest) {
        leaveRequestService.updateById(leaveRequest);
        return Result.success();
    }

    @PutMapping("/approve")
    public Result<Void> approve(@RequestBody LeaveRequest leaveRequest, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        if (leaveRequest.getId() == null) {
            return Result.error("申请ID不能为空");
        }
        LeaveRequest existing = leaveRequestService.getById(leaveRequest.getId());
        if (existing == null) {
            return Result.error("请假申请不存在");
        }
        // 每条请假申请只能审批一次
        if (!"pending".equals(existing.getStatus())) {
            return Result.error("该申请已审批，不能重复审批");
        }
        // 审核意见最多500字（与数据库 VARCHAR(500) 对齐，防止超长报错）
        if (leaveRequest.getRemark() != null && leaveRequest.getRemark().length() > 500) {
            return Result.error("审核意见最多500字");
        }
        // 拒绝时必填理由，避免无理由驳回
        if ("rejected".equals(leaveRequest.getStatus())
                && (leaveRequest.getRemark() == null || leaveRequest.getRemark().isBlank())) {
            return Result.error("拒绝请假必须填写理由");
        }
        Long approverId = (Long) request.getAttribute("userId");
        leaveRequest.setApproverId(approverId);
        // 审批人显示真实姓名，账号不能当姓名显示；查不到才退回登录账号
        User approver = userService.getById(approverId);
        leaveRequest.setApproverName(approver != null && approver.getRealName() != null && !approver.getRealName().isBlank()
            ? approver.getRealName() : (String) request.getAttribute("username"));
        leaveRequestService.updateById(leaveRequest);

        if ("approved".equals(leaveRequest.getStatus())) {
            LeaveRequest full = leaveRequestService.getById(leaveRequest.getId());
            dutyScheduleService.remove(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getUserId, full.getUserId())
                .eq(DutySchedule::getDutyDate, full.getStartDate())
                );
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        LeaveRequest leave = leaveRequestService.getById(id);
        if (leave == null) {
            return Result.error("请假申请不存在");
        }
        String role = (String) request.getAttribute("role");
        // 普通用户只能撤销自己名下、且仍处于待审核状态的申请
        if ("user".equals(role)) {
            Long userId = (Long) request.getAttribute("userId");
            if (!leave.getUserId().equals(userId)) {
                return Result.error("只能撤销自己的请假申请");
            }
            if (!"pending".equals(leave.getStatus())) {
                return Result.error("该申请已审批，无法撤销");
            }
        }
        leaveRequestService.removeById(id);
        return Result.success();
    }
}
