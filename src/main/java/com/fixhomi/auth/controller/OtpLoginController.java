package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.EmailOtpLoginRequest;
import com.fixhomi.auth.dto.EmailOtpVerifyRequest;
import com.fixhomi.auth.dto.PhoneOtpLoginRequest;
import com.fixhomi.auth.dto.PhoneOtpVerifyRequest;
import com.fixhomi.auth.service.OtpLoginService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for passwordless OTP-based login.
 * 
 * Supports two passwordless authentication methods:
 * 1. Phone OTP Login - Send OTP to phone number and verify to login
 * 2. Email OTP Login - Send OTP to email address and verify to login
 * 
 * These endpoints are public (no authentication required) and are designed
 * for React Native mobile app integration.
 */
@RestController
@RequestMapping("/api/auth/login")
@Tag(name = "OTP Login", description = "Passwordless OTP-based login endpoints")
public class OtpLoginController {

    private static final Logger logger = LoggerFactory.getLogger(OtpLoginController.class);

    private final OtpLoginService otpLoginService;

    public OtpLoginController(OtpLoginService otpLoginService) {
        this.otpLoginService = otpLoginService;
    }

    // ==================== PHONE OTP LOGIN ====================

    @Operation(
        summary = "Send Phone Login OTP",
        description = "Send a 6-digit OTP to the user's phone number for passwordless login. " +
                      "Creates a new user if phone number is not registered."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "OTP sent successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid phone number format"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Too many requests - rate limited"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Failed to send OTP"
        )
    })
    @PostMapping("/phone/send-otp")
    public ResponseEntity<?> sendPhoneLoginOtp(@Valid @RequestBody PhoneOtpLoginRequest request) {
        logger.info("Phone OTP login request for: {}", maskPhoneNumber(request.getPhoneNumber()));
        
        try {
            String maskedPhone = otpLoginService.sendPhoneLoginOtp(request.getPhoneNumber());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP sent successfully to " + maskedPhone,
                "maskedPhone", maskedPhone,
                "expiresInMinutes", 5
            ));
        } catch (Exception e) {
            logger.error("Error sending phone login OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage() != null ? e.getMessage() : "An error occurred while sending OTP"
            ));
        }
    }

    @Operation(
        summary = "Verify Phone Login OTP",
        description = "Verify the OTP sent to phone number and complete passwordless login. " +
                      "Returns JWT access token and refresh token on success."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or expired OTP"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found"
        )
    })
    @PostMapping("/phone/verify")
    public ResponseEntity<?> verifyPhoneLoginOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        logger.info("Phone OTP verification for: {}", maskPhoneNumber(request.getPhoneNumber()));
        
        try {
            LoginResponse loginResponse = otpLoginService.verifyPhoneLoginOtp(
                request.getPhoneNumber(), 
                request.getOtp()
            );
            
            logger.info("Phone OTP login successful for: {}", maskPhoneNumber(request.getPhoneNumber()));
            return ResponseEntity.ok(loginResponse);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Phone OTP verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error verifying phone login OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "An error occurred during verification"
            ));
        }
    }

    // ==================== EMAIL OTP LOGIN ====================

    @Operation(
        summary = "Send Email Login OTP",
        description = "Send a 6-digit OTP to the user's email address for passwordless login. " +
                      "User must have a registered account with the email."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "OTP sent successfully",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid email format"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Email not registered"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Too many requests - rate limited"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Failed to send OTP"
        )
    })
    @PostMapping("/email/send-otp")
    public ResponseEntity<?> sendEmailLoginOtp(@Valid @RequestBody EmailOtpLoginRequest request) {
        logger.info("Email OTP login request for: {}", maskEmail(request.getEmail()));
        
        try {
            String maskedEmail = otpLoginService.sendEmailLoginOtp(request.getEmail());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP sent successfully to " + maskedEmail,
                "maskedEmail", maskedEmail,
                "expiresInMinutes", 5
            ));
        } catch (IllegalArgumentException e) {
            logger.warn("Email OTP login request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error sending email login OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage() != null ? e.getMessage() : "An error occurred while sending OTP"
            ));
        }
    }

    @Operation(
        summary = "Verify Email Login OTP",
        description = "Verify the OTP sent to email address and complete passwordless login. " +
                      "Returns JWT access token and refresh token on success."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or expired OTP"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found"
        )
    })
    @PostMapping("/email/verify")
    public ResponseEntity<?> verifyEmailLoginOtp(@Valid @RequestBody EmailOtpVerifyRequest request) {
        logger.info("Email OTP verification for: {}", maskEmail(request.getEmail()));
        
        try {
            LoginResponse loginResponse = otpLoginService.verifyEmailLoginOtp(
                request.getEmail(), 
                request.getOtp()
            );
            
            logger.info("Email OTP login successful for: {}", maskEmail(request.getEmail()));
            return ResponseEntity.ok(loginResponse);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Email OTP verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error verifying email login OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "An error occurred during verification"
            ));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Mask phone number for logging (privacy protection).
     * Example: +1234567890 -> +123***7890
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(len - 4);
    }

    /**
     * Mask email for logging (privacy protection).
     * Example: user@example.com -> u***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
