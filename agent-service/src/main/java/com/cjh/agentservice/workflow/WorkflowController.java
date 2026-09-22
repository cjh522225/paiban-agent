package com.cjh.agentservice.workflow;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final HolidayConfigService holidayConfigService;

    public WorkflowController(HolidayConfigService holidayConfigService) {
        this.holidayConfigService = holidayConfigService;
    }

    public record PreviewRequest(@NotBlank String text) {
    }

    public record ApplyRequest(@NotBlank String draftToken) {
    }

    @PostMapping("/holiday/preview")
    public Map<String, Object> preview(@RequestBody PreviewRequest request) {
        return holidayConfigService.preview(request.text());
    }

    @PostMapping("/holiday/apply")
    public Map<String, Object> apply(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody ApplyRequest request) {
        return holidayConfigService.apply(request.draftToken(), authorization);
    }
}
