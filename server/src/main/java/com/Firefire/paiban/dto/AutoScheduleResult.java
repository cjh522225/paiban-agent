package com.Firefire.paiban.dto;

import lombok.Data;
import java.util.List;

@Data
public class AutoScheduleResult {
    private int totalSchedules;
    private List<String> warnings;
}
