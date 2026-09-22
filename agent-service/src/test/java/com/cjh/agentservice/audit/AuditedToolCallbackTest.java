package com.cjh.agentservice.audit;

import com.cjh.agentservice.demo.DemoBusinessClient;
import com.cjh.agentservice.tools.paiban.PaibanTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuditedToolCallbackTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditTrail auditTrail = new AuditTrail();
    private final ToolCallback[] callbacks = Arrays.stream(
                    ToolCallbacks.from(new PaibanTools(new DemoBusinessClient(mapper), "demo-token", mapper)))
            .map(callback -> (ToolCallback) new AuditedToolCallback(callback, auditTrail))
            .toArray(ToolCallback[]::new);

    @Test
    void recordsSuccessfulToolCall() {
        ToolCallback currentWeek = find("currentWeek");
        String output = currentWeek.call("{}");
        assertThat(output).contains("currentWeek").contains("weekStart");
        assertThat(auditTrail.recent(1)).hasSize(1);
        assertThat(auditTrail.recent(1).get(0).tool()).isEqualTo("currentWeek");
        assertThat(auditTrail.recent(1).get(0).ok()).isTrue();
    }

    @Test
    void convertsBusinessErrorToStructuredErrorAndRecordsFailure() {
        ToolCallback locationSchedule = find("locationSchedule");
        String output = locationSchedule.call("{\"locationName\":\"不存在的楼\",\"type\":\"dormitory\"}");
        assertThat(output).contains("\"error\"").contains("没有找到地点");
        assertThat(auditTrail.recent(1).get(0).tool()).isEqualTo("locationSchedule");
        assertThat(auditTrail.recent(1).get(0).ok()).isFalse();
    }

    private ToolCallback find(String name) {
        return Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
