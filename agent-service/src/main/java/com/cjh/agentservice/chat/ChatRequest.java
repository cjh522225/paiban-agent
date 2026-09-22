package com.cjh.agentservice.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, Boolean allowWrites, String conversationId) {

    public boolean writesAllowed() {
        return Boolean.TRUE.equals(allowWrites);
    }
}
