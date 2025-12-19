package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.*;
import com.fixhomi.auth.service.EmailVerificationService;
import com.fixhomi.auth.service.PasswordResetService;
import com.fixhomi.auth.service.PhoneVerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for identity verification and password reset operations.
 * 
 * Public endpoints:
 * - POST /api/auth/forgot-password
 * - POST /api/auth/reset-password
 * - GET  /api/auth/reset-password/validate
 * - GET  /api/auth/email/verify
 * 
 * Authenticated endpoints:
 * - POST /api/auth/otp/send
 * - POST /api/auth/otp/verify
 * - POST /api/auth/email/send-verification
 */
@RestController
@RequestMapping("/api/auth")
public class VerificationController {

    @Autowired
    private PhoneVerificationService phoneVerificationService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordResetService passwordResetService;

    // ==================== PHONE OTP VERIFICATION ====================

    /**
     * Send OTP to authenticated user's phone number.
     * POST /api/auth/otp/send
     * 
     * Requires: Valid JWT token
     */
    @PostMapping("/otp/send")
    public ResponseEntity<VerificationResponse> sendOtp(Authentication authentication) {
        String email = authentication.getName();
        String maskedPhone = phoneVerificationService.sendOtp(email);
        
        return ResponseEntity.ok(VerificationResponse.success(
                "OTP sent successfully", maskedPhone));
    }

    /**
     * Verify OTP code for authenticated user.
     * POST /api/auth/otp/verify
     * 
     * Requires: Valid JWT token
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<VerificationResponse> verifyOtp(
            Authentication authentication,
            @Valid @RequestBody VerifyOtpRequest request) {
        
        String email = authentication.getName();
        phoneVerificationService.verifyOtp(email, request.getOtp());
        
        return ResponseEntity.ok(VerificationResponse.success(
                "Phone number verified successfully"));
    }

    // ==================== EMAIL VERIFICATION ====================

    /**
     * Send verification email to authenticated user.
     * POST /api/auth/email/send-verification
     * 
     * Requires: Valid JWT token
     */
    @PostMapping("/email/send-verification")
    public ResponseEntity<VerificationResponse> sendVerificationEmail(Authentication authentication) {
        String email = authentication.getName();
        String maskedEmail = emailVerificationService.sendVerificationEmail(email);
        
        return ResponseEntity.ok(VerificationResponse.success(
                "Verification email sent successfully", maskedEmail));
    }

    /**
     * Verify email using token from verification link.
     * GET /api/auth/email/verify?token=...
     * 
     * Public endpoint (no JWT required)
     */
    @GetMapping("/email/verify")
    public ResponseEntity<VerificationResponse> verifyEmail(@RequestParam String token) {
        String verifiedEmail = emailVerificationService.verifyEmail(token);
        
        return ResponseEntity.ok(VerificationResponse.success(
                "Email verified successfully", verifiedEmail));
    }

    // ==================== PASSWORD RESET ====================

    /**
     * Request password reset (forgot password).
     * POST /api/auth/forgot-password
     * 
     * Public endpoint (no JWT required)
     * Always returns 200 to prevent email enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<VerificationResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        
        passwordResetService.requestPasswordReset(request.getEmail());
        
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(VerificationResponse.success(
                "If your email is registered, you will receive a password reset link shortly."));
    }

    /**
     * Reset password using token.
     * POST /api/auth/reset-password
     * 
     * Public endpoint (no JWT required)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<VerificationResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        
        return ResponseEntity.ok(VerificationResponse.success(
                "Password reset successfully. Please login with your new password."));
    }

    /**
     * Validate password reset token (for frontend pre-check).
     * GET /api/auth/reset-password/validate?token=...
     * 
     * Public endpoint (no JWT required)
     */
    @GetMapping("/reset-password/validate")
    public ResponseEntity<VerificationResponse> validateResetToken(@RequestParam String token) {
        boolean valid = passwordResetService.validateToken(token);
        
        if (valid) {
            return ResponseEntity.ok(VerificationResponse.success("Token is valid"));
        } else {
            return ResponseEntity.badRequest().body(
                    new VerificationResponse(false, "Invalid or expired token"));
        }
    }
}
