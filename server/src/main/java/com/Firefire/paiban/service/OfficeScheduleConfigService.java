package com.Firefire.paiban.service;

import com.Firefire.paiban.entity.OfficeScheduleConfig;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OfficeScheduleConfigService extends IService<OfficeScheduleConfig> {

    /** 当前每节课值班人数（默认 4，1~20 内） */
    int getCapacity();

    /** 保存每节课人数（校验 1~20） */
    int saveCapacity(int capacity);
}
