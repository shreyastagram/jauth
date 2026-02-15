package com.fixhomi.auth.dto;

import com.fixhomi.auth.entity.Role;

/**
 * Response DTO for successful login.
 * Contains JWT access token, refresh token, and user information.
 */
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private String fullName;
    private Role role;
    private Long expiresIn; // seconds
    private Boolean isNewUser; // For Google OAuth - indicates if this is a new registration
    private String phoneNumber; // Phone number for cross-service sync
    private Boolean isPhoneVerified; // Phone verification status for cross-service sync

    // Constructors
    public LoginResponse() {
    }

    public LoginResponse(String accessToken, String refreshToken, Long userId, String email, String fullName, Role role, Long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.expiresIn = expiresIn;
        this.isNewUser = false;
        this.phoneNumber = null;
        this.isPhoneVerified = false;
    }

    public LoginResponse(String accessToken, String refreshToken, Long userId, String email, String fullName, Role role, Long expiresIn, String phoneNumber, Boolean isPhoneVerified) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.expiresIn = expiresIn;
        this.isNewUser = false;
        this.phoneNumber = phoneNumber;
        this.isPhoneVerified = isPhoneVerified;
    }

    public LoginResponse(String accessToken, String refreshToken, Long userId, String email, String fullName, Role role, Long expiresIn, Boolean isNewUser) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.expiresIn = expiresIn;
        this.isNewUser = isNewUser;
    }

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Boolean getIsNewUser() {
        return isNewUser;
    }

    public void setIsNewUser(Boolean isNewUser) {
        this.isNewUser = isNewUser;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getIsPhoneVerified() {
        return isPhoneVerified;
    }

    public void setIsPhoneVerified(Boolean isPhoneVerified) {
        this.isPhoneVerified = isPhoneVerified;
    }
}
