package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.Office;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.mapper.OfficeMapper;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.Firefire.paiban.service.OfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfficeServiceImpl extends ServiceImpl<OfficeMapper, Office> implements OfficeService {

    private final DutyScheduleMapper dutyScheduleMapper;

    @Override
    public boolean removeById(java.io.Serializable id) {
        dutyScheduleMapper.delete(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, "office")
                .eq(DutySchedule::getLocationId, String.valueOf(id)));
        return super.removeById(id);
    }
}
