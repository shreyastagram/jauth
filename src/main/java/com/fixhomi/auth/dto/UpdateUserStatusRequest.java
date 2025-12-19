package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating user account status.
 * Used by PATCH /api/admin/users/{userId}/status endpoint.
 */
public class UpdateUserStatusRequest {

    @NotNull(message = "isActive status is required")
    private Boolean isActive;

    // Constructors
    public UpdateUserStatusRequest() {
    }

    public UpdateUserStatusRequest(Boolean isActive) {
        this.isActive = isActive;
    }

    // Getters and Setters
    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
