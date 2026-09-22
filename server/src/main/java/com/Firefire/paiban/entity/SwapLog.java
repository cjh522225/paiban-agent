package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("swap_log")
public class SwapLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private Integer weekNumber;
    private Long scheduleIdA;
    private Long userIdA;
    private String userNameA;
    private Long scheduleIdB;
    private Long userIdB;
    private String userNameB;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
