package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

/** 调休补课映射：补班日=makeupDate，补的是replacedDate那天的课（空闲时间按replacedDate的星期+单双周计算） */
@Data
@TableName("duty_adjustment_makeup")
public class DutyAdjustmentMakeup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adjustmentId;
    private LocalDate makeupDate;
    private LocalDate replacedDate;
}
