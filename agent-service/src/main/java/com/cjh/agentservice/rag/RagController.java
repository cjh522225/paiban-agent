package com.cjh.agentservice.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return ragService.status();
    }

    @PostMapping("/eval")
    public Map<String, Object> evaluate() {
        return ragService.evaluate();
    }
}
