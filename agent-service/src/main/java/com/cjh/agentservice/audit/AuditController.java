package com.cjh.agentservice.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditTrail auditTrail;

    public AuditController(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @GetMapping("/recent")
    public List<AuditTrail.Entry> recent(@RequestParam(defaultValue = "50") int limit) {
        return auditTrail.recent(limit);
    }
}
