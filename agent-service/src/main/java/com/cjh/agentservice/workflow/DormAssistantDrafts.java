package com.cjh.agentservice.workflow;

import java.util.List;

public record DormAssistantDrafts() {

    public record ViolationDraft(
            String violationType,
            String severity,
            String reason,
            String suggestion,
            List<String> warnings) {
    }

    public record ImportAnalysis(
            String summary,
            List<String> causes,
            List<String> suggestions) {
    }
}
