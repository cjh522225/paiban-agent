package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("office")
public class Office {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code; // 办公室编码
    private String name; // 办公室名称
    private String building; // 楼栋
    private String floor; // 楼层
    private String description;
    private String gender;
    private Integer status; // 0-禁用, 1-启用
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
