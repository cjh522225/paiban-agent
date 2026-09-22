package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("swap_request")
public class SwapRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private String type; // office/dormitory
    private Integer weekNumber;
    private LocalDate dutyDate;
    private String timeSlot;
    private String locationName;
    private String targetDay;
    private String targetSlot;
    private String reason;
    private String status; // pending/done
    private Long handledBy;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
