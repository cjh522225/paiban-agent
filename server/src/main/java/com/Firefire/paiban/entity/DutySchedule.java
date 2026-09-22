package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("duty_schedule")
public class DutySchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type; // dormitory-宿舍, office-办公室
    private String locationId; // 宿舍楼或办公室ID
    private String locationName;
    private Long userId;
    private String userName;
    private LocalDate dutyDate;
    private String timeSlot; // 时间段,如"1-2节"
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
