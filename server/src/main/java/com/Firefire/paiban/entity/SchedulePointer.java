package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 排班指针：记录各身份池当前排到的用户位置，跨周持续（按用户表顺序循环） */
@Data
@TableName("schedule_pointer")
public class SchedulePointer {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 池标识：dorm_patrol/dorm_sitting_male/dorm_sitting_female/dorm_knock_male/dorm_knock_female/office */
    private String poolKey;
    /** 上次排到的用户ID（下次从其后一位开始；NULL 表示从表头开始） */
    private Long currentUserId;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
