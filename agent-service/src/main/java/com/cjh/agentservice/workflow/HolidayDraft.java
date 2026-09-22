package com.cjh.agentservice.workflow;

import java.util.List;

public record HolidayDraft(
        String summary,
        List<HolidayItem> holidays,
        List<MakeupItem> makeups,
        List<String> warnings) {

    public record HolidayItem(String name, String startDate, String endDate) {
    }

    public record MakeupItem(String makeupDate, String replacesDate, String note) {
    }
}
