package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("multi_duty_request")
public class MultiDutyRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type; // office/dormitory
    private Integer weekStart;
    private Integer weekEnd;
    private String note;
    private String status; // pending/approved/rejected
    private Long approverId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
