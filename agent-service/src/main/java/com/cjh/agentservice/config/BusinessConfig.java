package com.cjh.agentservice.config;

import com.cjh.agentservice.business.BusinessClient;
import com.cjh.agentservice.business.RestBusinessClient;
import com.cjh.agentservice.business.ServiceAccountAuth;
import com.cjh.agentservice.business.TokenResolver;
import com.cjh.agentservice.demo.DemoBusinessClient;
import com.cjh.agentservice.demo.DemoDormClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class BusinessConfig {

    @Bean
    public BusinessClient businessClient(AgentProperties properties, ObjectMapper objectMapper) {
        String mode = properties.getMode() == null ? "" : properties.getMode().toLowerCase();
        if ("demo".equals(mode)) {
            return new DemoBusinessClient(objectMapper);
        }
        if ("dorm-demo".equals(mode)) {
            return new DemoDormClient(objectMapper);
        }
        return new RestBusinessClient(activeBusiness(properties).getBaseUrl(), objectMapper);
    }

    @Bean
    public TokenResolver tokenResolver(AgentProperties properties) {
        return new TokenResolver(ServiceAccountAuth.from(properties.getMode(), activeBusiness(properties)));
    }

    private AgentProperties.Business activeBusiness(AgentProperties properties) {
        return "dorm".equalsIgnoreCase(properties.getMode()) ? properties.getDorm() : properties.getPaiban();
    }
}
