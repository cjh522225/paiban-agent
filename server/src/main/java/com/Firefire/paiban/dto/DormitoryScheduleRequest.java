package com.Firefire.paiban.dto;

import lombok.Data;
import java.util.List;

@Data
public class DormitoryScheduleRequest {
    private List<Long> dormitoryIds;
    private String startDate;
    private String endDate;
    private String weekParity;
}
