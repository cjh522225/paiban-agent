package com.cjh.agentservice.tools.paiban;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.demo.DemoBusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;

class PaibanToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaibanTools tools = new PaibanTools(new DemoBusinessClient(mapper), fakeToken(), mapper);

    private static String fakeToken() {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    new ObjectMapper().writeValueAsBytes(Map.of("userId", 1, "username", "demo1", "role", "admin")));
            return "eyJhbGciOiJIUzI1NiJ9." + payload + ".signature";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void currentWeekReturnsSemesterAndWeekRange() {
        String result = tools.currentWeek();
        assertThat(result).contains("currentWeek").contains("weekStart").contains("weekEnd");
        assertThat(result).contains("演示数据");
    }

    @Test
    void myScheduleContainsWeekdayAndTimeSlotLabel() {
        String result = tools.mySchedule(null, null);
        assertThat(result).contains("巡班(19:00-21:00)");
        assertThat(result).contains("周一").contains("周三");
        assertThat(result).contains("演示用户A");
    }

    @Test
    void locationScheduleResolvesByName() {
        String result = tools.locationSchedule("A1栋", "dormitory", null, null);
        assertThat(result).contains("\"location\":\"A1栋\"");
        assertThat(result).contains("演示用户B");
    }

    @Test
    void locationScheduleResolvesByCode() {
        String result = tools.locationSchedule("A1", "dormitory", null, null);
        assertThat(result).contains("演示用户A");
    }

    @Test
    void scheduleAnalysisResolvesNumericUserIdFirst() {
        String result = tools.scheduleAnalysis("2", null);
        assertThat(result).contains("演示用户B");
    }

    @Test
    void unknownLocationFails() {
        assertThatThrownBy(() -> tools.locationSchedule("不存在的楼", "dormitory", null, null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到地点");
    }

    @Test
    void userLookupStripsPasswordField() {
        String result = tools.userLookup("演示");
        assertThat(result).contains("演示用户A").contains("演示用户B");
        assertThat(result).doesNotContain("password");
    }

    @Test
    void scheduleAnalysisDetectsCooling() {
        String result = tools.scheduleAnalysis("演示用户C", null);
        assertThat(result).contains("\"coolingSuspected\":true");
        assertThat(result).contains("冷却");
    }

    @Test
    void scheduleAnalysisForCurrentUserFromJwt() {
        String result = tools.scheduleAnalysis(null, null);
        assertThat(result).contains("thisWeekDuty");
        assertThat(result).contains("演示用户A");
    }

    @Test
    void disciplineRecordsFiltersByType() {
        String result = tools.disciplineRecords("absent", null, null);
        assertThat(result).contains("未到岗").doesNotContain("迟到 10 分钟");
        JsonNode node;
        try {
            node = mapper.readTree(result);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(node.path("data").isArray()).isTrue();
    }
}
