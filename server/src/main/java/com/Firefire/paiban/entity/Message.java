package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String type; // duty-值班通知, approval-审批通知, system-系统通知等
    private String senderName;
    private Long senderId;
    private String receivers; // 接收人ID,逗号分隔
    private String attachment; // 附件路径,逗号分隔
    private Integer status; // 0-草稿, 1-已发布
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 非数据库字段：统计用 =====
    @TableField(exist = false)
    private Integer viewCount;  // 已查看人数
    @TableField(exist = false)
    private Integer readCount;  // 已读人数
    @TableField(exist = false)
    private Integer isRead;     // 当前用户是否已读 0/1
    @TableField(exist = false)
    private Integer targetCount; // 通知人数
}
