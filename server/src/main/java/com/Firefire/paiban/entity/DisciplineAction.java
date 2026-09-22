package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("discipline_action")
public class DisciplineAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String action; // 通报批评/退出发展
    private Integer absentCount;
    private Integer handled; // 0/1
    private Long handledBy;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
