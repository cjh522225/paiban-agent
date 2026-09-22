package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("leave_request")
public class LeaveRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private String dutyType; // dormitory-宿舍, office-办公室
    private String leaveType; // sick-病假, personal-事假, annual-年假等
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer days;
    private String reason;
    private Integer weekNumber; // 第几周
    private Integer dayOfWeek; // 星期几 (1=周一..5=周五)
    private Long locationId; // 值班地点ID
    private String locationName; // 值班地点名称
    private String attachment; // 附件路径,逗号分隔
    private String status; // pending-待审批, approved-已批准, rejected-已拒绝
    private Long approverId;
    private String approverName;
    private String remark; // 审批备注
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
