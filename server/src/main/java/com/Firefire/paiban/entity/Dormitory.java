package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dormitory")
public class Dormitory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String building;
    private Integer floor;
    private String description;
    private String gender;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
