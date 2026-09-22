package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 办公室排班全局配置（每节课值班人数等） */
@Data
@TableName("office_schedule_config")
public class OfficeScheduleConfig {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 每节课值班人数(1~20)，默认 4 */
    private Integer slotCapacity;

    private LocalDateTime updateTime;
}
