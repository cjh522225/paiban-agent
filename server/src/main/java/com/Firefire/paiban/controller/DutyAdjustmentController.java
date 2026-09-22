package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.DutyAdjustment;
import com.Firefire.paiban.entity.DutyAdjustmentMakeup;
import com.Firefire.paiban.mapper.DutyAdjustmentMakeupMapper;
import com.Firefire.paiban.mapper.DutyAdjustmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 调休管理：管理员在学期调整里设置调休周期 + 补课映射 */
@RestController
@RequestMapping("/api/duty-adjustments")
@RequiredArgsConstructor
public class DutyAdjustmentController {

    private final DutyAdjustmentMapper adjustmentMapper;
    private final DutyAdjustmentMakeupMapper makeupMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<DutyAdjustment> list = adjustmentMapper.selectList(
            new LambdaQueryWrapper<DutyAdjustment>().orderByDesc(DutyAdjustment::getId));
        List<DutyAdjustmentMakeup> allMakeups = makeupMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DutyAdjustment a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("startDate", a.getStartDate() != null ? a.getStartDate().toString() : null);
            m.put("endDate", a.getEndDate() != null ? a.getEndDate().toString() : null);
            m.put("note", a.getNote());
            m.put("makeups", allMakeups.stream()
                .filter(x -> x.getAdjustmentId() != null && x.getAdjustmentId().equals(a.getId()))
                .map(x -> {
                    Map<String, Object> mm = new HashMap<>();
                    mm.put("makeupDate", x.getMakeupDate() != null ? x.getMakeupDate().toString() : null);
                    mm.put("replacedDate", x.getReplacedDate() != null ? x.getReplacedDate().toString() : null);
                    return mm;
                }).collect(Collectors.toList()));
            result.add(m);
        }
        return Result.success(result);
    }

    @PostMapping
    public Result<Void> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        DutyAdjustment adj = new DutyAdjustment();
        adj.setStartDate(LocalDate.parse((String) body.get("startDate")));
        adj.setEndDate(LocalDate.parse((String) body.get("endDate")));
        adj.setNote((String) body.get("note"));
        adjustmentMapper.insert(adj);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> makeups = (List<Map<String, Object>>) body.get("makeups");
        if (makeups != null) {
            for (Map<String, Object> mk : makeups) {
                DutyAdjustmentMakeup d = new DutyAdjustmentMakeup();
                d.setAdjustmentId(adj.getId());
                d.setMakeupDate(LocalDate.parse((String) mk.get("makeupDate")));
                d.setReplacedDate(LocalDate.parse((String) mk.get("replacedDate")));
                makeupMapper.insert(d);
            }
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
            return Result.error("无权限");
        }
        makeupMapper.delete(new LambdaQueryWrapper<DutyAdjustmentMakeup>().eq(DutyAdjustmentMakeup::getAdjustmentId, id));
        adjustmentMapper.deleteById(id);
        return Result.success();
    }
}
