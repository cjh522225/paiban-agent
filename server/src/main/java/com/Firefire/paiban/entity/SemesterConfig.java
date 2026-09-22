package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("semester_config")
public class SemesterConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private LocalDate startDate;
    private Integer totalWeeks;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
