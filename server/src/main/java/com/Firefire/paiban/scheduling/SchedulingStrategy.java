package com.Firefire.paiban.scheduling;

import com.Firefire.paiban.dto.AutoScheduleResult;
import java.util.Map;

public interface SchedulingStrategy {
    String getType();
    AutoScheduleResult generate(Map<String, Object> params);
}
