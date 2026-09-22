package com.cjh.agentservice.tools.paiban;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaibanWriteToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private BusinessClient client;
    private PaibanWriteTools tools;

    @BeforeEach
    void setUp() {
        client = mock(BusinessClient.class);
        when(client.put(anyString(), anyString(), any())).thenReturn(mapper.nullNode());
        when(client.post(anyString(), anyString(), any())).thenReturn(mapper.nullNode());
        when(client.delete(anyString(), anyString())).thenReturn(mapper.nullNode());
        when(client.delete(anyString(), anyString(), any())).thenReturn(mapper.nullNode());
        tools = new PaibanWriteTools(client, "test-token", mapper);
    }

    @SuppressWarnings("unchecked")
    @Test
    void approveLeaveSendsPutWithBody() {
        String result = tools.approveLeave(7L, "通过", "同意");
        assertThat(result).contains("\"ok\":true").contains("PUT /api/leaves/approve");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).put(eq("test-token"), eq("/api/leaves/approve"), body.capture());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent).containsEntry("id", 7L).containsEntry("status", "approved").containsEntry("remark", "同意");
    }

    @Test
    void approveLeaveRejectedRequiresRemark() {
        assertThatThrownBy(() -> tools.approveLeave(7L, "rejected", ""))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("驳回请假必须填写理由");
    }

    @Test
    void approveSwapRequestUsesQueryAction() {
        tools.approveSwapRequest(3L, "approved");
        verify(client).post(eq("test-token"), eq("/api/swap-requests/3/approve?action=approved"), isNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    void clearSchedulesDeletesWithBody() {
        tools.clearSchedules("dormitory", 1L, "2026-10-01", "2026-10-07");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).delete(eq("test-token"), eq("/api/schedules/batch"), body.capture());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent).containsEntry("type", "dormitory").containsEntry("locationId", 1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void saveMyAvailabilityParsesSlots() {
        tools.saveMyAvailability("odd:1:1,both:5:2");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("test-token"), eq("/api/availabilities/batch"), body.capture());
        List<Map<String, Object>> slots = (List<Map<String, Object>>) body.getValue();
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0)).containsEntry("weekParity", "odd").containsEntry("dayOfWeek", 1).containsEntry("timeSlotId", 1L);
        assertThat(slots.get(1)).containsEntry("weekParity", "both").containsEntry("dayOfWeek", 5);
    }

    @Test
    void saveMyAvailabilityRejectsBadFormat() {
        assertThatThrownBy(() -> tools.saveMyAvailability("odd:9:1"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("星期取值应为 1-7");
    }

    @Test
    void submitLeaveMapsChineseLeaveType() {
        tools.submitLeave("2026-12-25", null, "病假", "dormitory", "测试");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("test-token"), eq("/api/leaves"), body.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent).containsEntry("leaveType", "sick")
                .containsEntry("startDate", "2026-12-25")
                .containsEntry("endDate", "2026-12-25");
    }

    @Test
    void batchDeleteUsersSendsIdArray() {
        tools.batchDeleteUsers("12,13");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("test-token"), eq("/api/users/batch-delete"), body.capture());
        assertThat(body.getValue()).isEqualTo(List.of(12L, 13L));
    }

    @Test
    void sendMessageRequiresTitle() {
        assertThatThrownBy(() -> tools.sendMessage("  ", "内容", "system", null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("消息标题不能为空");
    }

    @Test
    void resultDataIsConvertedToPlainJson() {
        JsonNode data = mapper.valueToTree(Map.of("totalSchedules", 12, "warnings", List.of()));
        when(client.post(anyString(), eq("/api/schedules/auto/dormitory"), any())).thenReturn(data);
        String result = tools.generateDormitorySchedule("2026-10-01", "2026-10-07", "1,2");
        assertThat(result).contains("\"totalSchedules\":12").contains("POST /api/schedules/auto/dormitory");
    }

    @SuppressWarnings("unchecked")
    @Test
    void manageDormitorySupportsCreateAndDelete() {
        tools.manageDormitory("create", null, "A9", "A9 宿舍楼", "A区", 6, "男", 1);
        ArgumentCaptor<Object> createBody = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("test-token"), eq("/api/dormitories"), createBody.capture());
        Map<String, Object> sent = (Map<String, Object>) createBody.getValue();
        assertThat(sent).containsEntry("code", "A9").containsEntry("name", "A9 宿舍楼").containsEntry("floor", 6);

        tools.manageDormitory("delete", 5L, null, null, null, null, null, null);
        verify(client).delete("test-token", "/api/dormitories/5");
    }

    @SuppressWarnings("unchecked")
    @Test
    void saveDormIdentityPermissionParsesMatrix() {
        tools.saveDormIdentityPermission("巡班:1:1:1,坐班:1:0:0");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).put(eq("test-token"), eq("/api/dorm-identity-permission"), body.capture());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getValue();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("identity", "巡班").containsEntry("allowDuty", 1);
        assertThat(rows.get(1)).containsEntry("identity", "坐班").containsEntry("allowDuty", 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void manageDutyAdjustmentParsesMakeups() {
        tools.manageDutyAdjustment("create", null, "2026-10-01", "2026-10-07", "国庆调休", "2026-09-27=2026-10-05");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("test-token"), eq("/api/duty-adjustments"), body.capture());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent).containsEntry("startDate", "2026-10-01").containsEntry("endDate", "2026-10-07");
        List<Map<String, Object>> makeups = (List<Map<String, Object>>) sent.get("makeups");
        assertThat(makeups).hasSize(1);
        assertThat(makeups.get(0)).containsEntry("makeupDate", "2026-09-27").containsEntry("replacedDate", "2026-10-05");
    }

    @Test
    void updateOfficeSlotCapacityValidatesRange() {
        assertThatThrownBy(() -> tools.updateOfficeSlotCapacity(25))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("1~20");
    }

    @Test
    void addDisciplineRecordValidatesType() {
        assertThatThrownBy(() -> tools.addDisciplineRecord(3L, "unknown", "2026-10-01", null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("无效记录类型");
    }
}
