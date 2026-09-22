package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.DisciplineAction;
import com.Firefire.paiban.entity.DisciplineRecord;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.service.DisciplineActionService;
import com.Firefire.paiban.service.DisciplineRecordService;
import com.Firefire.paiban.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/discipline")
@RequiredArgsConstructor
public class DisciplineController {

    private final DisciplineRecordService disciplineRecordService;
    private final DisciplineActionService disciplineActionService;
    private final UserService userService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String recordType,
                                                  HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        LambdaQueryWrapper<DisciplineRecord> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(DisciplineRecord::getUserId, userId);
        if (startDate != null) w.ge(DisciplineRecord::getDutyDate, startDate);
        if (endDate != null) w.le(DisciplineRecord::getDutyDate, endDate);
        if (recordType != null && !recordType.isBlank()) w.eq(DisciplineRecord::getRecordType, recordType);
        w.orderByDesc(DisciplineRecord::getDutyDate);
        List<DisciplineRecord> list = disciplineRecordService.list(w);
        return Result.success(list.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("dutyDate", r.getDutyDate());
            m.put("recordType", r.getRecordType());
            m.put("note", r.getNote());
            m.put("createTime", r.getCreateTime());
            User u = userService.getById(r.getUserId());
            m.put("userName", u != null ? u.getRealName() : "");
            return m;
        }).collect(Collectors.toList()));
    }

    @PostMapping
    public Result<Void> save(@RequestBody DisciplineRecord record, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        if (record.getUserId() == null || record.getRecordType() == null) {
            return Result.error("参数不完整");
        }
        if (!"late".equals(record.getRecordType()) && !"absent".equals(record.getRecordType())) {
            return Result.error("无效记录类型");
        }
        record.setId(null);
        disciplineRecordService.save(record);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        disciplineRecordService.removeById(id);
        return Result.success();
    }

    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        List<DisciplineRecord> all = disciplineRecordService.list();
        Map<Long, Map<String, Object>> map = new TreeMap<>();
        for (DisciplineRecord r : all) {
            Map<String, Object> m = map.computeIfAbsent(r.getUserId(), k -> {
                Map<String, Object> x = new HashMap<>();
                x.put("userId", k);
                x.put("lateCount", 0);
                x.put("absentCount", 0);
                x.put("absentFromLate", 0);
                return x;
            });
            if ("late".equals(r.getRecordType())) m.put("lateCount", (int) m.get("lateCount") + 1);
            else if ("absent".equals(r.getRecordType())) m.put("absentCount", (int) m.get("absentCount") + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : map.values()) {
            int late = (int) m.get("lateCount");
            int absent = (int) m.get("absentCount");
            int absentFromLate = late / 3;
            int totalAbsent = absent + absentFromLate;
            m.put("absentFromLate", absentFromLate);
            m.put("totalAbsent", totalAbsent);
            m.put("status", totalAbsent >= 5 ? "退出发展" : totalAbsent >= 3 ? "通报批评" : "正常");
            User u = userService.getById((Long) m.get("userId"));
            m.put("userName", u != null ? u.getRealName() : "");
            result.add(m);
        }
        result.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("totalAbsent")).reversed());
        generateActions(result);
        return Result.success(result);
    }

    // 幂等生成未处理处罚提醒: 旷班>=3通报批评, >=5退出发展。
    // 修复Bug1：同用户同等级提醒"已处理过"就不再重建（否则每次重算都复活已处理提醒）；
    // 顺带清理"明细已删到阈值以下但仍残留的未处理提醒"(记录与action脱节)。
    private void generateActions(List<Map<String, Object>> summary) {
        Map<Long, Integer> totalByUser = new HashMap<>();
        for (Map<String, Object> m : summary) {
            Long userId = (Long) m.get("userId");
            int totalAbsent = (int) m.get("totalAbsent");
            totalByUser.put(userId, totalAbsent);
            if (totalAbsent >= 5) ensureAction(userId, "退出发展", totalAbsent);
            if (totalAbsent >= 3) ensureAction(userId, "通报批评", totalAbsent);
        }
        // 失效未处理提醒清理：当前旷班数已不达该等级阈值(含该用户已无记录) → 删除残留的未处理提醒
        List<DisciplineAction> unhandled = disciplineActionService.list(
                new LambdaQueryWrapper<DisciplineAction>().eq(DisciplineAction::getHandled, 0));
        for (DisciplineAction a : unhandled) {
            int t = totalByUser.getOrDefault(a.getUserId(), 0);
            boolean meets = ("通报批评".equals(a.getAction()) && t >= 3)
                    || ("退出发展".equals(a.getAction()) && t >= 5);
            if (!meets) disciplineActionService.removeById(a.getId());
        }
    }

    private void ensureAction(Long userId, String action, int absentCount) {
        // 幂等：只要该用户该等级 action 已存在(无论是否已处理)就不再新建，避免"已处理"被重算复活
        long exists = disciplineActionService.count(new LambdaQueryWrapper<DisciplineAction>()
                .eq(DisciplineAction::getUserId, userId)
                .eq(DisciplineAction::getAction, action));
        if (exists > 0) return;
        DisciplineAction a = new DisciplineAction();
        a.setUserId(userId);
        a.setAction(action);
        a.setAbsentCount(absentCount);
        a.setHandled(0);
        disciplineActionService.save(a);
    }

    @GetMapping("/actions")
    public Result<List<Map<String, Object>>> actions(@RequestParam(required = false) String handled,
                                                     HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        LambdaQueryWrapper<DisciplineAction> w = new LambdaQueryWrapper<>();
        if ("0".equals(handled) || "1".equals(handled)) {
            w.eq(DisciplineAction::getHandled, Integer.parseInt(handled));
        }
        w.orderByDesc(DisciplineAction::getAbsentCount).orderByAsc(DisciplineAction::getHandled);
        return Result.success(disciplineActionService.list(w).stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("userId", a.getUserId());
            m.put("action", a.getAction());
            m.put("absentCount", a.getAbsentCount());
            m.put("handled", a.getHandled());
            m.put("createTime", a.getCreateTime());
            User u = userService.getById(a.getUserId());
            m.put("userName", u != null ? u.getRealName() : "");
            return m;
        }).collect(Collectors.toList()));
    }

    @PostMapping("/actions/{id}/handle")
    public Result<Void> handleAction(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) return Result.error("无权限");
        DisciplineAction a = disciplineActionService.getById(id);
        if (a == null) return Result.error("记录不存在");
        a.setHandled(1);
        a.setHandledBy((Long) request.getAttribute("userId"));
        a.setHandledAt(java.time.LocalDateTime.now());
        disciplineActionService.updateById(a);
        return Result.success();
    }
}
