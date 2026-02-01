package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.DeleteAccountRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UpdateProfileRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user account operations.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get authenticated user's profile.
     * GET /api/users/me
     *
     * @return user profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile() {
        String email = getCurrentUserEmail();
        UserProfileResponse profile = userService.getUserProfile(email);
        return ResponseEntity.ok(profile);
    }

    /**
     * Update authenticated user's profile.
     * PUT /api/users/profile
     *
     * @param request update profile request with fullName and/or phoneNumber
     * @return updated user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String email = getCurrentUserEmail();
        UserProfileResponse profile = userService.updateProfile(email, request);
        return ResponseEntity.ok(profile);
    }

    /**
     * Change password for authenticated user.
     * POST /api/users/change-password
     *
     * @param request change password request
     * @return success message
     */
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String email = getCurrentUserEmail();
        MessageResponse response = userService.changePassword(email, request);
        return ResponseEntity.ok(response);
    }

    // ==================== ACCOUNT DELETION ====================

    /**
     * Request OTP for account deletion.
     * POST /api/users/delete-account/request-otp
     *
     * Sends OTP to user's verified phone number for account deletion confirmation.
     * Requires: Valid JWT token and verified phone number.
     *
     * @return success message with masked phone number
     */
    @PostMapping("/delete-account/request-otp")
    public ResponseEntity<MessageResponse> requestDeleteAccountOtp() {
        String email = getCurrentUserEmail();
        String maskedPhone = userService.requestDeleteAccountOtp(email);
        return ResponseEntity.ok(new MessageResponse(
            "OTP sent to " + maskedPhone + ". Enter the code to confirm account deletion."
        ));
    }

    /**
     * Delete account with OTP verification.
     * DELETE /api/users/account
     *
     * Verifies OTP and permanently deletes the user account.
     * This is a destructive operation - account cannot be recovered.
     *
     * @param request delete account request with OTP
     * @return success message
     */
    @DeleteMapping("/account")
    public ResponseEntity<MessageResponse> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        String email = getCurrentUserEmail();
        MessageResponse response = userService.deleteAccountWithOtp(email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account by ID (for internal service-to-service calls).
     * DELETE /api/users/{userId}
     *
     * This endpoint is used by Node.js backend to delete user from Java Auth.
     * INTERNAL USE ONLY - should be protected by service-to-service auth in production.
     *
     * @param userId the user ID to delete
     * @return success message
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> deleteAccountById(@PathVariable Long userId) {
        MessageResponse response = userService.deleteAccountById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current authenticated user's email from SecurityContext.
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName(); // email is set as principal in JwtAuthenticationFilter
    }
}
