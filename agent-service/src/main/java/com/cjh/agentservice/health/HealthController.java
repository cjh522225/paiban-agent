package com.cjh.agentservice.health;

import com.cjh.agentservice.config.AgentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供前端悬浮球探测服务状态（/api/** 已有 CORS，浏览器可直接读取）。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final AgentProperties agentProperties;

    public HealthController(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "agent-service");
        result.put("mode", agentProperties.getMode());
        result.put("time", Instant.now().toString());
        return result;
    }
}
