package com.Firefire.paiban.entity;


import com.baomidou.mybatisplus.annotation.*;
        import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_availability")
public class UserAvailability {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String weekParity;
    private Integer dayOfWeek;
    private Long timeSlotId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}