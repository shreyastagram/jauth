package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for changing or setting password.
 * Used by POST /api/users/change-password endpoint.
 * 
 * For OAuth users setting their first password, currentPassword can be empty.
 * For existing password users, currentPassword is required.
 */
public class ChangePasswordRequest {

    // Note: @NotBlank removed to allow OAuth users to set password without currentPassword
    // Validation is done in UserService based on whether user has existing password
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
    private String newPassword;

    // Constructors
    public ChangePasswordRequest() {
    }

    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    // Getters and Setters
    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
