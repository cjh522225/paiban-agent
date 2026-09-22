package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("discipline_record")
public class DisciplineRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate dutyDate;
    private String recordType; // late-迟到, absent-缺勤
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
