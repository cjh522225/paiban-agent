package com.cjh.agentservice.tools;

import com.cjh.agentservice.audit.AuditTrail;
import com.cjh.agentservice.business.TokenResolver;
import com.cjh.agentservice.config.AgentProperties;
import com.cjh.agentservice.demo.DemoBusinessClient;
import com.cjh.agentservice.rag.RagService;
import com.cjh.agentservice.workflow.DormAssistantService;
import com.cjh.agentservice.workflow.HolidayConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditTrail auditTrail = new AuditTrail();
    private final RagService ragService = mock(RagService.class);
    private final HolidayConfigService holidayConfigService = mock(HolidayConfigService.class);
    private final DormAssistantService dormAssistantService = mock(DormAssistantService.class);

    @Test
    void dormModeExposesDormToolsInsteadOfPaibanTools() {
        List<String> names = toolNames(registry("dorm").toolsFor(null, false));
        assertThat(names).containsExactlyInAnyOrder(
                "dormOverview", "availableBeds", "studentDorm", "roomOccupancy",
                "counselorScope", "violationStats", "buildingList", "userLookup", "searchPolicy",
                "classifyViolation", "analyzeImportErrors");
        assertThat(names).doesNotContain("currentWeek", "mySchedule", "locationSchedule",
                "previewHolidayConfig", "applyHolidayConfig");
    }

    @Test
    void dormModeExposesNoWriteToolsEvenWhenWritesAllowed() {
        List<String> names = toolNames(registry("dorm").toolsFor(null, true));
        assertThat(names).doesNotContain("applyHolidayConfig");
    }

    @Test
    void paibanModeKeepsExistingTools() {
        List<String> names = toolNames(registry("paiban").toolsFor(null, false));
        assertThat(names).contains("currentWeek", "mySchedule", "userLookup", "searchPolicy", "previewHolidayConfig");
        assertThat(names).doesNotContain("dormOverview", "availableBeds");
    }

    @Test
    void writeToolsOnlyAppearWhenWritesAllowed() {
        List<String> readOnly = toolNames(registry("paiban").toolsFor(null, false));
        List<String> writable = toolNames(registry("paiban").toolsFor(null, true));
        assertThat(readOnly).doesNotContain("approveLeave", "approveSwapRequest", "generateDormitorySchedule", "sendMessage");
        assertThat(writable).contains("approveLeave", "approveSwapRequest", "approveMultiDuty",
                "generateDormitorySchedule", "generateOfficeSchedule", "clearSchedules", "swapSchedules",
                "sendMessage", "updateMessage", "deleteMessage", "createUser", "updateUser", "deleteUser",
                "batchDeleteUsers", "saveMyAvailability", "handleDisciplineAction", "deleteHoliday",
                "submitLeave", "cancelLeave", "submitSwapRequest", "completeSwapRequest", "cancelSwapRequest",
                "submitMultiDuty", "cancelMultiDuty", "deleteSchedule", "markAllMessagesRead", "deleteAvailability",
                "createSchedule", "addDisciplineRecord", "deleteDisciplineRecord", "markMessageRead",
                "saveMessageDraft", "deleteMessageDraft", "manageDormitory", "manageOffice", "manageTimeSlot",
                "saveSemester", "updateOfficeSlotCapacity", "saveDormIdentityPermission", "manageDutyAdjustment");
    }

    private ToolRegistry registry(String mode) {
        AgentProperties properties = new AgentProperties();
        properties.setMode(mode);
        return new ToolRegistry(new DemoBusinessClient(mapper), new TokenResolver(null), auditTrail, mapper,
                ragService, holidayConfigService, properties, dormAssistantService);
    }

    private List<String> toolNames(List<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
    }
}
