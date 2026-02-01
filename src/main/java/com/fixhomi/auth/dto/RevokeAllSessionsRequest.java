package com.fixhomi.auth.dto;

/**
 * Request DTO for revoking all sessions except current device.
 */
public class RevokeAllSessionsRequest {

    private String exceptDeviceId;

    // Constructors
    public RevokeAllSessionsRequest() {
    }

    public RevokeAllSessionsRequest(String exceptDeviceId) {
        this.exceptDeviceId = exceptDeviceId;
    }

    // Getters and Setters
    public String getExceptDeviceId() {
        return exceptDeviceId;
    }

    public void setExceptDeviceId(String exceptDeviceId) {
        this.exceptDeviceId = exceptDeviceId;
    }
}
