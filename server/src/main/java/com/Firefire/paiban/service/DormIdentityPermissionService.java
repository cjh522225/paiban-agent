package com.Firefire.paiban.service;

import com.Firefire.paiban.entity.DormIdentityPermission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DormIdentityPermissionService extends IService<DormIdentityPermission> {

    /** 全量授权（身份 → 可顶岗位集合，岗位用 巡班/坐班/敲灯） */
    Map<String, Set<String>> allowedByIdentity();

    /** 某值班身份可顶岗位集合（无则空） */
    Set<String> allowedSlots(String identity);

    /** 某值班身份是否有权顶某岗位 */
    boolean canFill(String identity, String slot);

    /** 整体保存（对角强制为已授权；自动补全缺失身份为对角默认） */
    void saveMatrix(List<DormIdentityPermission> rows);
}
