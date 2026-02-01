package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.*;
import com.fixhomi.auth.service.EmailVerificationService;
import com.fixhomi.auth.service.PasswordResetService;
import com.fixhomi.auth.service.PhoneVerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    
    @Value("${fixhomi.app.deep-link-scheme:fixhomi}")
    private String appDeepLinkScheme;

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
     * Returns an HTML page with:
     * - Success/error message
     * - Automatic redirect to app via deep link
     * - Manual "Open App" button as fallback
     * 
     * Public endpoint (no JWT required)
     */
    @GetMapping(value = "/email/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        String html;
        try {
            String verifiedEmail = emailVerificationService.verifyEmail(token);
            html = buildSuccessHtml(verifiedEmail);
        } catch (Exception e) {
            html = buildErrorHtml(e.getMessage());
        }
        return ResponseEntity.ok(html);
    }
    
    /**
     * Build success HTML page with app redirect
     */
    private String buildSuccessHtml(String email) {
        String deepLink = appDeepLinkScheme + "://email-verified?email=" + email + "&status=success";
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email Verified - FixHomi</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .card {
                        background: white;
                        border-radius: 20px;
                        padding: 40px;
                        max-width: 400px;
                        width: 100%%;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    }
                    .icon {
                        width: 80px;
                        height: 80px;
                        background: #10B981;
                        border-radius: 50%%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 24px;
                    }
                    .icon svg { width: 40px; height: 40px; fill: white; }
                    h1 { color: #1F2937; font-size: 24px; margin-bottom: 12px; }
                    p { color: #6B7280; font-size: 16px; line-height: 1.5; margin-bottom: 24px; }
                    .email { color: #4F46E5; font-weight: 600; }
                    .btn {
                        display: inline-block;
                        background: linear-gradient(135deg, #f67c16 0%%, #e85d04 100%%);
                        color: white;
                        text-decoration: none;
                        padding: 14px 32px;
                        border-radius: 12px;
                        font-weight: 600;
                        font-size: 16px;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    .btn:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 20px rgba(246, 124, 22, 0.3);
                    }
                    .redirect-text { 
                        color: #9CA3AF; 
                        font-size: 14px; 
                        margin-top: 16px;
                    }
                    .loader {
                        width: 20px;
                        height: 20px;
                        border: 2px solid #E5E7EB;
                        border-top-color: #4F46E5;
                        border-radius: 50%%;
                        animation: spin 1s linear infinite;
                        display: inline-block;
                        margin-right: 8px;
                        vertical-align: middle;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">
                        <svg viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
                    </div>
                    <h1>Email Verified!</h1>
                    <p>Your email <span class="email">%s</span> has been successfully verified.</p>
                    <a href="%s" class="btn">Open FixHomi App</a>
                    <p class="redirect-text">
                        <span class="loader"></span>
                        Redirecting to app...
                    </p>
                </div>
                <script>
                    // Try to redirect to app after 1.5 seconds
                    setTimeout(function() {
                        window.location.href = '%s';
                    }, 1500);
                </script>
            </body>
            </html>
            """.formatted(email, deepLink, deepLink);
    }
    
    /**
     * Build error HTML page
     */
    private String buildErrorHtml(String errorMessage) {
        String deepLink = appDeepLinkScheme + "://email-verified?status=error&message=" + 
            java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verification Failed - FixHomi</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .card {
                        background: white;
                        border-radius: 20px;
                        padding: 40px;
                        max-width: 400px;
                        width: 100%%;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    }
                    .icon {
                        width: 80px;
                        height: 80px;
                        background: #EF4444;
                        border-radius: 50%%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 24px;
                    }
                    .icon svg { width: 40px; height: 40px; fill: white; }
                    h1 { color: #1F2937; font-size: 24px; margin-bottom: 12px; }
                    p { color: #6B7280; font-size: 16px; line-height: 1.5; margin-bottom: 24px; }
                    .error { color: #EF4444; font-weight: 500; }
                    .btn {
                        display: inline-block;
                        background: linear-gradient(135deg, #f67c16 0%%, #e85d04 100%%);
                        color: white;
                        text-decoration: none;
                        padding: 14px 32px;
                        border-radius: 12px;
                        font-weight: 600;
                        font-size: 16px;
                        transition: transform 0.2s, box-shadow 0.2s;
                        margin-right: 10px;
                    }
                    .btn:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 20px rgba(246, 124, 22, 0.3);
                    }
                    .btn-secondary {
                        background: #6B7280;
                    }
                    .btn-secondary:hover {
                        box-shadow: 0 10px 20px rgba(107, 114, 128, 0.3);
                    }
                    .buttons { display: flex; justify-content: center; flex-wrap: wrap; gap: 10px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">
                        <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
                    </div>
                    <h1>Verification Failed</h1>
                    <p class="error">%s</p>
                    <p>The verification link may have expired or already been used. Please request a new verification email from the app.</p>
                    <div class="buttons">
                        <a href="%s" class="btn">Open App</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(errorMessage, deepLink);
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

    // ==================== OTP-BASED PASSWORD RESET ====================

    /**
     * Request password reset via phone OTP.
     * POST /api/auth/forgot-password/phone
     * 
     * Public endpoint (no JWT required)
     * Sends OTP to the phone number associated with the account.
     */
    @PostMapping("/forgot-password/phone")
    public ResponseEntity<VerificationResponse> forgotPasswordPhone(
            @Valid @RequestBody ForgotPasswordPhoneRequest request) {
        
        String maskedPhone = passwordResetService.requestPasswordResetOtp(request.getPhoneNumber());
        
        return ResponseEntity.ok(VerificationResponse.success(
                "OTP sent successfully", maskedPhone));
    }

    /**
     * Verify OTP and reset password.
     * POST /api/auth/forgot-password/phone/verify
     * 
     * Public endpoint (no JWT required)
     * Verifies OTP and resets password in one step.
     */
    @PostMapping("/forgot-password/phone/verify")
    public ResponseEntity<VerificationResponse> verifyOtpAndResetPassword(
            @Valid @RequestBody VerifyOtpAndResetPasswordRequest request) {
        
        passwordResetService.verifyOtpAndResetPassword(
                request.getPhoneNumber(),
                request.getOtp(),
                request.getNewPassword()
        );
        
        return ResponseEntity.ok(VerificationResponse.success(
                "Password reset successfully. Please login with your new password."));
    }
}
