package com.Firefire.paiban.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.Firefire.paiban.entity.SemesterConfig;

public interface SemesterConfigService extends IService<SemesterConfig> {
    SemesterConfig getCurrent();
}
