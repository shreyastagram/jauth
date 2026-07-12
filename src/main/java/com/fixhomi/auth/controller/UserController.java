package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.DeleteAccountRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.PhoneChangeSendOtpRequest;
import com.fixhomi.auth.dto.PhoneChangeVerifyRequest;
import com.fixhomi.auth.dto.SetEmailRequest;
import com.fixhomi.auth.dto.UpdateProfileRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.service.PhoneVerificationService;
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

    @Autowired
    private PhoneVerificationService phoneVerificationService;

    /**
     * Get authenticated user's profile.
     * GET /api/users/me
     *
     * @return user profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile() {
        Long userId = getCurrentUserId();
        UserProfileResponse profile = userService.getUserProfile(userId);
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
        Long userId = getCurrentUserId();
        UserProfileResponse profile = userService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    /**
     * Set or change the authenticated user's email (add-email-later / change-email).
     * POST /api/users/email
     *
     * <p>Stores the email UNVERIFIED and sends a fresh verification link. Changing
     * an already-verified email re-triggers verification. The NoeFix caller mirrors
     * the email value into the Mongo profile after this returns 200.
     *
     * @param request the new email
     * @return updated profile (with isEmailVerified=false)
     */
    @PostMapping("/email")
    public ResponseEntity<UserProfileResponse> setEmail(@Valid @RequestBody SetEmailRequest request) {
        Long userId = getCurrentUserId();
        UserProfileResponse profile = userService.setEmail(userId, request.getEmail());
        return ResponseEntity.ok(profile);
    }

    /**
     * Start a verify-then-replace phone change (also add-first-number and
     * re-verify): sends an OTP to the NEW number. The account's current number
     * stays active and verified until the OTP is verified.
     * POST /api/users/phone/change/send-otp
     *
     * @param request the new phone number
     * @return masked new number confirmation
     */
    @PostMapping("/phone/change/send-otp")
    public ResponseEntity<MessageResponse> sendPhoneChangeOtp(@Valid @RequestBody PhoneChangeSendOtpRequest request) {
        Long userId = getCurrentUserId();
        String maskedPhone = phoneVerificationService.sendChangeOtp(userId, request.getPhoneNumber());
        return ResponseEntity.ok(new MessageResponse("OTP sent to " + maskedPhone));
    }

    /**
     * Complete the phone change: verify the OTP sent to the NEW number; only on
     * success the account's phoneNumber is replaced and marked verified — in a
     * single transaction.
     * POST /api/users/phone/change/verify
     *
     * @param request the new phone number and OTP
     * @return the updated profile (phoneNumber = new, isPhoneVerified = true)
     */
    @PostMapping("/phone/change/verify")
    public ResponseEntity<UserProfileResponse> verifyPhoneChangeOtp(@Valid @RequestBody PhoneChangeVerifyRequest request) {
        Long userId = getCurrentUserId();
        phoneVerificationService.verifyChangeOtp(userId, request.getPhoneNumber(), request.getOtp());
        return ResponseEntity.ok(userService.getUserProfile(userId));
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
        Long userId = getCurrentUserId();
        MessageResponse response = userService.changePassword(userId, request);
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
        Long userId = getCurrentUserId();
        String maskedPhone = userService.requestDeleteAccountOtp(userId);
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
        Long userId = getCurrentUserId();
        MessageResponse response = userService.deleteAccountWithOtp(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account by ID (for authenticated admin or self-deletion).
     * DELETE /api/users/{userId}
     *
     * Only allows deletion if the authenticated user is deleting their own account
     * or is an ADMIN. Prevents IDOR attacks.
     *
     * @param userId the user ID to delete
     * @return success message
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> deleteAccountById(@PathVariable Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new MessageResponse("Authentication required"));
        }

        Long currentUserId = Long.valueOf(authentication.getName());
        // Verify the authenticated user owns this account or is an admin
        boolean isAuthorized = userService.isUserAuthorizedForDeletion(currentUserId, userId);
        if (!isAuthorized) {
            return ResponseEntity.status(403).body(new MessageResponse("Not authorized to delete this account"));
        }

        MessageResponse response = userService.deleteAccountById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current authenticated user's email from SecurityContext.
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Long.valueOf(authentication.getName()); // userId is set as principal in JwtAuthenticationFilter
    }
}
