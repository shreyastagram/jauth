package com.fixhomi.auth.dto;

import com.fixhomi.auth.entity.Role;

import java.time.LocalDateTime;

/**
 * Response DTO for user profile information.
 * Used by GET /api/users/me endpoint.
 * Returns complete user profile including verification status.
 */
public class UserProfileResponse {

    private Long userId;
    private String email;
    private String phoneNumber;
    private String fullName;
    private Role role;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private Boolean isPhoneVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private Boolean hasPassword;  // Indicates if user has set a password (false for OAuth-only users)

    // Constructors
    public UserProfileResponse() {
    }

    /**
     * Full constructor with all user profile fields.
     */
    public UserProfileResponse(Long userId, String email, String phoneNumber, String fullName, 
                               Role role, Boolean isActive, Boolean isEmailVerified, 
                               Boolean isPhoneVerified, LocalDateTime createdAt, 
                               LocalDateTime updatedAt, LocalDateTime lastLoginAt,
                               Boolean hasPassword) {
        this.userId = userId;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;
        this.role = role;
        this.isActive = isActive;
        this.isEmailVerified = isEmailVerified;
        this.isPhoneVerified = isPhoneVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
        this.hasPassword = hasPassword;
    }

    // Getters and Setters
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getIsEmailVerified() {
        return isEmailVerified;
    }

    public void setIsEmailVerified(Boolean isEmailVerified) {
        this.isEmailVerified = isEmailVerified;
    }

    public Boolean getIsPhoneVerified() {
        return isPhoneVerified;
    }

    public void setIsPhoneVerified(Boolean isPhoneVerified) {
        this.isPhoneVerified = isPhoneVerified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Boolean getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }
}
