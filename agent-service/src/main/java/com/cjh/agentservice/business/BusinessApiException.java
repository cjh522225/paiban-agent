package com.cjh.agentservice.business;

public class BusinessApiException extends RuntimeException {

    private final int status;

    public BusinessApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
