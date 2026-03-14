package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for mobile Apple Sign-In authentication.
 * Mobile apps use @invertase/react-native-apple-authentication SDK
 * to get an identity token (JWT), then send it here for verification.
 *
 * Apple provides email and name ONLY on the first sign-in.
 * Subsequent sign-ins return only the identity token (no email/name in token if user hid email).
 * The backend must handle both cases.
 */
public class AppleMobileAuthRequest {

    @NotBlank(message = "Apple identity token is required")
    private String identityToken;

    /**
     * Apple authorization code — single-use, for server-to-server validation.
     * More secure than identity token alone because it can only be used once.
     */
    private String authorizationCode;

    /**
     * User's full name — Apple only provides this on FIRST sign-in.
     * Must be sent from client and saved immediately.
     */
    private String fullName;

    /**
     * User's email — Apple may provide a relay email (privaterelay.appleid.com).
     * Only available on first sign-in if user chose "Hide My Email".
     */
    private String email;

    /**
     * Apple user identifier — stable across sign-ins for same app.
     * Format: 001234.abcdef1234567890abcdef1234567890.1234
     */
    private String appleUserId;

    /**
     * Optional role for new user registration.
     * Allowed values: "USER" (default), "SERVICE_PROVIDER"
     * Only applied when creating a NEW user.
     */
    @Pattern(regexp = "^(USER|SERVICE_PROVIDER)?$", message = "Role must be USER or SERVICE_PROVIDER")
    private String role;

    /**
     * Auth mode: "login" or "signup".
     * - "login": Only authenticate existing users.
     * - "signup": Only register new users.
     * - null/empty: Legacy auto-register-or-login.
     */
    @Pattern(regexp = "^(login|signup)?$", message = "Mode must be 'login' or 'signup'")
    private String mode;

    // Optional: device info
    private String deviceId;
    private String deviceType;
    private String appVersion;

    // Constructors
    public AppleMobileAuthRequest() {}

    public AppleMobileAuthRequest(String identityToken) {
        this.identityToken = identityToken;
    }

    // Getters and Setters
    public String getIdentityToken() { return identityToken; }
    public void setIdentityToken(String identityToken) { this.identityToken = identityToken; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAppleUserId() { return appleUserId; }
    public void setAppleUserId(String appleUserId) { this.appleUserId = appleUserId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
}
