package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.PhoneSignupSendOtpRequest;
import com.fixhomi.auth.dto.PhoneSignupVerifyRequest;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.service.PhoneSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phone-number SIGNUP for service USERS only. Hard-locked to {@link com.fixhomi.auth.entity.Role#USER}
 * — providers are out of scope and cannot use this endpoint. Public (unauthenticated) by design;
 * security comes from OTP possession + rate limiting in {@link PhoneSignupService}.
 */
@RestController
@RequestMapping("/api/auth/signup")
@Tag(name = "Phone Signup", description = "USER-only phone-number signup (account creation)")
public class PhoneSignupController {

    private static final Logger logger = LoggerFactory.getLogger(PhoneSignupController.class);

    private final PhoneSignupService phoneSignupService;

    public PhoneSignupController(PhoneSignupService phoneSignupService) {
        this.phoneSignupService = phoneSignupService;
    }

    @Operation(
        summary = "Send phone-signup OTP",
        description = "Start a phone-number signup for a new USER account. Captures the prospective " +
                "user's full name and dispatches an OTP via SMS. No user row is created at this stage. " +
                "Rejects with 409 if a verified+active account already owns the phone number."
    )
    @PostMapping("/phone/send-otp")
    public ResponseEntity<?> sendPhoneSignupOtp(@Valid @RequestBody PhoneSignupSendOtpRequest request) {
        logger.info("Phone signup OTP request for: {}", maskPhoneNumber(request.getPhoneNumber()));
        try {
            String maskedPhone = phoneSignupService.sendPhoneSignupOtp(
                    request.getPhoneNumber(), request.getFullName());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP sent successfully to " + maskedPhone,
                    "maskedPhone", maskedPhone,
                    "expiresInMinutes", 5
            ));
        } catch (DuplicateResourceException e) {
            logger.warn("Phone signup rejected (already registered): {}",
                    maskPhoneNumber(request.getPhoneNumber()));
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "code", "ALREADY_REGISTERED",
                    "message", "An account with this phone number already exists. Please log in."
            ));
        } catch (TooManyRequestsException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "success", false,
                    "code", "TOO_MANY_REQUESTS",
                    "message", e.getMessage()
            ));
        } catch (VerificationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "code", "VERIFICATION_FAILED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error sending phone signup OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "An error occurred while sending OTP"
            ));
        }
    }

    @Operation(
        summary = "Verify phone-signup OTP and create USER",
        description = "Verify the OTP from /send-otp and create a new USER account. Email persists as " +
                "NULL for phone-only users. Returns access + refresh tokens, ready for immediate use."
    )
    @PostMapping("/phone/verify")
    public ResponseEntity<?> verifyPhoneSignupOtp(@Valid @RequestBody PhoneSignupVerifyRequest request) {
        logger.info("Phone signup OTP verification for: {}", maskPhoneNumber(request.getPhoneNumber()));
        try {
            LoginResponse response = phoneSignupService.verifyPhoneSignupOtp(
                    request.getPhoneNumber(), request.getOtp());
            response.setIsNewUser(true);
            logger.info("Phone signup successful for userId={}", response.getUserId());
            return ResponseEntity.ok(response);
        } catch (DuplicateResourceException e) {
            logger.warn("Phone signup verify rejected (already registered)");
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "code", "ALREADY_REGISTERED",
                    "message", "An account with this phone number already exists. Please log in."
            ));
        } catch (VerificationException e) {
            String msg = e.getMessage();
            String code = "INVALID_OTP";
            if (msg != null) {
                if (msg.contains("expired")) code = "OTP_EXPIRED";
                else if (msg.contains("Maximum")) code = "MAX_ATTEMPTS_EXCEEDED";
                else if (msg.contains("No pending")) code = "OTP_EXPIRED";
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "code", code,
                    "message", msg != null ? msg : "OTP verification failed"
            ));
        } catch (Exception e) {
            logger.error("Error verifying phone signup OTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "An error occurred during verification"
            ));
        }
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(len - 4);
    }
}
