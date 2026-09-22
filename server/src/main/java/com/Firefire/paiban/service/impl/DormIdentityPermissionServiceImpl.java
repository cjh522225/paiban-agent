package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.DormIdentityPermission;
import com.Firefire.paiban.mapper.DormIdentityPermissionMapper;
import com.Firefire.paiban.service.DormIdentityPermissionService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DormIdentityPermissionServiceImpl
        extends ServiceImpl<DormIdentityPermissionMapper, DormIdentityPermission>
        implements DormIdentityPermissionService {

    /** 三个固定值班身份，每身份的"自身岗位"（对角） */
    private static final Map<String, String> SELF_SLOT = Map.of(
            "巡班", "巡班",
            "坐班", "坐班",
            "敲灯", "敲灯");

    @Override
    public Map<String, Set<String>> allowedByIdentity() {
        Map<String, DormIdentityPermission> rows = new HashMap<>();
        for (DormIdentityPermission p : list()) {
            rows.put(p.getIdentity(), p);
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (String identity : SELF_SLOT.keySet()) {
            result.put(identity, slotSet(identity, rows.get(identity)));
        }
        return result;
    }

    @Override
    public Set<String> allowedSlots(String identity) {
        if (identity == null || identity.isBlank()) return Collections.emptySet();
        return allowedByIdentity().getOrDefault(identity, Collections.emptySet());
    }

    @Override
    public boolean canFill(String identity, String slot) {
        if (identity == null || slot == null) return false;
        return allowedSlots(identity).contains(slot);
    }

    @Override
    public void saveMatrix(List<DormIdentityPermission> rows) {
        Map<String, DormIdentityPermission> input = new HashMap<>();
        if (rows != null) {
            for (DormIdentityPermission r : rows) {
                if (r.getIdentity() == null) continue;
                input.put(r.getIdentity().trim(), r);
            }
        }
        for (String identity : SELF_SLOT.keySet()) {
            DormIdentityPermission row = input.get(identity);
            DormIdentityPermission saved = row != null ? row : new DormIdentityPermission();
            saved.setIdentity(identity);
            // 对角强制已授权
            boolean isPatrol = "巡班".equals(identity), isDuty = "坐班".equals(identity), isKnock = "敲灯".equals(identity);
            saved.setAllowPatrol(intOf(saved.getAllowPatrol()) | (isPatrol ? 1 : 0));
            saved.setAllowDuty(intOf(saved.getAllowDuty()) | (isDuty ? 1 : 0));
            saved.setAllowKnock(intOf(saved.getAllowKnock()) | (isKnock ? 1 : 0));
            upsert(saved);
        }
    }

    private void upsert(DormIdentityPermission row) {
        DormIdentityPermission exist = getOne(new LambdaQueryWrapper<DormIdentityPermission>()
                .eq(DormIdentityPermission::getIdentity, row.getIdentity()));
        if (exist != null) {
            row.setId(exist.getId());
            updateById(row);
        } else {
            row.setId(null);
            save(row);
        }
    }

    private Set<String> slotSet(String identity, DormIdentityPermission p) {
        Set<String> set = new HashSet<>();
        if (p == null) {
            // 兜底：默认对角（自身岗位）
            set.add(identity);
            return set;
        }
        if (intOf(p.getAllowPatrol()) == 1) set.add("巡班");
        if (intOf(p.getAllowDuty()) == 1) set.add("坐班");
        if (intOf(p.getAllowKnock()) == 1) set.add("敲灯");
        return set;
    }

    private int intOf(Integer v) { return v == null ? 0 : v; }
}
