package com.cjh.agentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** demo | paiban | dorm */
    private String mode = "demo";

    private final Business paiban = new Business();

    private final Business dorm = new Business();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isDormMode() {
        return mode != null && mode.toLowerCase().startsWith("dorm");
    }

    public boolean isDemoMode() {
        if (mode == null) {
            return false;
        }
        String normalized = mode.toLowerCase();
        return "demo".equals(normalized) || "dorm-demo".equals(normalized);
    }

    public Business getPaiban() {
        return paiban;
    }

    public Business getDorm() {
        return dorm;
    }

    public static class Business {

        private String baseUrl = "http://localhost:8080";

        private String serviceUsername = "admin";

        private String servicePassword = "change-me";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getServiceUsername() {
            return serviceUsername;
        }

        public void setServiceUsername(String serviceUsername) {
            this.serviceUsername = serviceUsername;
        }

        public String getServicePassword() {
            return servicePassword;
        }

        public void setServicePassword(String servicePassword) {
            this.servicePassword = servicePassword;
        }
    }
}
