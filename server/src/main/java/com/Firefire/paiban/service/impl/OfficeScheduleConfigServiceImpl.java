package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.OfficeScheduleConfig;
import com.Firefire.paiban.mapper.OfficeScheduleConfigMapper;
import com.Firefire.paiban.service.OfficeScheduleConfigService;
import org.springframework.stereotype.Service;

@Service
public class OfficeScheduleConfigServiceImpl
        extends ServiceImpl<OfficeScheduleConfigMapper, OfficeScheduleConfig>
        implements OfficeScheduleConfigService {

    private static final int DEFAULT_CAPACITY = 4;

    @Override
    public int getCapacity() {
        OfficeScheduleConfig cfg = getById(1);
        int cap = cfg != null && cfg.getSlotCapacity() != null ? cfg.getSlotCapacity() : DEFAULT_CAPACITY;
        return Math.max(1, Math.min(20, cap));
    }

    @Override
    public int saveCapacity(int capacity) {
        int cap = Math.max(1, Math.min(20, capacity));
        OfficeScheduleConfig cfg = new OfficeScheduleConfig();
        cfg.setId(1);
        cfg.setSlotCapacity(cap);
        saveOrUpdate(cfg);
        return cap;
    }
}
