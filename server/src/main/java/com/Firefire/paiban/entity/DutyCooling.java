package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 值班冷却表：记录最近两周值过班的人，用于防止"连续两周被排"。
 * 排班时排除本周与上周排过的人（week_number >= 本周-1）；隔一周冷却结束（week_number 早于本周-1）删除。
 * 多排用户不进此表。
 */
@Data
@TableName("duty_cooling")
public class DutyCooling {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 值班用户ID */
    private Long userId;
    /** 值班周次（按学期开始日推算，第1/2/3…周） */
    private Integer weekNumber;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
