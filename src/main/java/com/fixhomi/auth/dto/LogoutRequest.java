package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for logout (revoking refresh token).
 */
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    // Constructors
    public LogoutRequest() {
    }

    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // Getters and Setters
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
