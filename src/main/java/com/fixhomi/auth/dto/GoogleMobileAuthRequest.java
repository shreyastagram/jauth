package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for mobile Google Sign-In authentication.
 * Mobile apps use Google Sign-In SDK to get an ID token,
 * then send it to this backend for verification and JWT issuance.
 * 
 * Supports role selection: USER (default) or SERVICE_PROVIDER
 */
public class GoogleMobileAuthRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    /**
     * Optional role for new user registration.
     * Allowed values: "USER" (default), "SERVICE_PROVIDER"
     * Only applied when creating a NEW user.
     * Existing users keep their current role.
     */
    @Pattern(regexp = "^(USER|SERVICE_PROVIDER)?$", message = "Role must be USER or SERVICE_PROVIDER")
    private String role;

    // Optional: device info for analytics/security
    private String deviceId;
    private String deviceType; // "ios" or "android"
    private String appVersion;

    // Constructors
    public GoogleMobileAuthRequest() {
    }

    public GoogleMobileAuthRequest(String idToken) {
        this.idToken = idToken;
    }

    // Getters and Setters
    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
