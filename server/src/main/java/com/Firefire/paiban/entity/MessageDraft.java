package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_draft")
public class MessageDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private String title;
    private String content;
    private String type;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
