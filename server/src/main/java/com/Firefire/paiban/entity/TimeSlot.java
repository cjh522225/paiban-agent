package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("time_slot")
public class TimeSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String label; // 显示标签,如"1-2节"
    private String startTime; // 开始时间
    private String endTime; // 结束时间
    private String period; // 时间段显示,如"08:00-09:40"
    private Integer sortOrder;
    private Integer status; // 0-禁用, 1-启用
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
