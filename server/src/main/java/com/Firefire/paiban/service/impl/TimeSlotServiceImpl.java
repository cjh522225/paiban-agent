package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.TimeSlot;
import com.Firefire.paiban.mapper.TimeSlotMapper;
import com.Firefire.paiban.service.TimeSlotService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimeSlotServiceImpl extends ServiceImpl<TimeSlotMapper, TimeSlot> implements TimeSlotService {
    @Override
    public List<TimeSlot> list() {
        // 按 label 去重（数据库可能存在重复记录），保留每组第一条
        return lambdaQuery()
            .eq(TimeSlot::getStatus, 1)
            .orderByAsc(TimeSlot::getSortOrder)
            .list()
            .stream()
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(TimeSlot::getLabel, t -> t, (a, b) -> a, LinkedHashMap::new),
                m -> new ArrayList<>(m.values())));
    }
}
