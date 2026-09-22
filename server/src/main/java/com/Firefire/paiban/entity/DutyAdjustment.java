package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 调休周期配置：管理员在学期调整设置；宿舍排班覆盖[start-1,end-1]，办公室按补课映射排班 */
@Data
@TableName("duty_adjustment")
public class DutyAdjustment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
