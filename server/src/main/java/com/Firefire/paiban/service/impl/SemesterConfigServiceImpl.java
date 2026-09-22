package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.SemesterConfig;
import com.Firefire.paiban.mapper.SemesterConfigMapper;
import com.Firefire.paiban.service.SemesterConfigService;
import org.springframework.stereotype.Service;

@Service
public class SemesterConfigServiceImpl extends ServiceImpl<SemesterConfigMapper, SemesterConfig> implements SemesterConfigService {
    @Override
    public SemesterConfig getCurrent() {
        return list().stream().findFirst().orElse(null);
    }
}
