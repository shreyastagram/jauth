package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for mobile Google Sign-In authentication.
 * Mobile apps use Google Sign-In SDK to get an ID token,
 * then send it to this backend for verification and JWT issuance.
 */
public class GoogleMobileAuthRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

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
}
