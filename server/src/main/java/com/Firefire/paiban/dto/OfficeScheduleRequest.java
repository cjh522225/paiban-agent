package com.Firefire.paiban.dto;

import lombok.Data;
import java.util.List;

@Data
public class OfficeScheduleRequest {
    private List<Long> officeIds;
    private String startDate;
    private String endDate;
    private String weekParity;
}
