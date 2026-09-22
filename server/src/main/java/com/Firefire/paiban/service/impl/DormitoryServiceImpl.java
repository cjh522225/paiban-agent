package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.Dormitory;
import com.Firefire.paiban.entity.DutySchedule;
import com.Firefire.paiban.mapper.DormitoryMapper;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.Firefire.paiban.service.DormitoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DormitoryServiceImpl extends ServiceImpl<DormitoryMapper, Dormitory> implements DormitoryService {

    private final DutyScheduleMapper dutyScheduleMapper;

    @Override
    public boolean removeById(java.io.Serializable id) {
        dutyScheduleMapper.delete(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getType, "dormitory")
                .eq(DutySchedule::getLocationId, String.valueOf(id)));
        return super.removeById(id);
    }
}
