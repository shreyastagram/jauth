package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for phone number verification via Twilio Verify API.
 * Uses Twilio's managed OTP service for generation, delivery, and verification.
 */
@Service
public class PhoneVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneVerificationService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmsService smsService;

    // OTP configuration is managed by Twilio Verify API
    // Twilio handles rate limiting, OTP generation, expiration, and attempt limits automatically

    /**
     * Send OTP to user's phone number via Twilio Verify API.
     * Twilio handles OTP generation, delivery, expiry, and rate limiting.
     *
     * @param userEmail user's email (from JWT)
     * @return masked phone number
     */
    public String sendOtp(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new VerificationException("No phone number registered for this account");
        }

        if (user.getIsPhoneVerified()) {
            throw new VerificationException("Phone number is already verified");
        }

        String phoneNumber = user.getPhoneNumber();

        // Start verification via Twilio Verify API
        // Twilio handles rate limiting, OTP generation, and expiry internally
        boolean sent = smsService.startVerification(phoneNumber);
        if (!sent) {
            logger.warn("Failed to start phone verification for: {}", maskPhoneNumber(phoneNumber));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("📱 Phone verification OTP sent via Twilio Verify for user: {} (phone: {})", 
                userEmail, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP code via Twilio Verify API.
     * Twilio validates the OTP internally with attempt tracking.
     *
     * @param userEmail user's email (from JWT)
     * @param otpCode OTP code to verify
     */
    @Transactional
    public void verifyOtp(String userEmail, String otpCode) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new VerificationException("No phone number registered for this account");
        }

        if (user.getIsPhoneVerified()) {
            throw new VerificationException("Phone number is already verified");
        }

        String phoneNumber = user.getPhoneNumber();

        // Verify OTP via Twilio Verify API
        // Twilio handles attempt counting and expiry internally
        boolean verified = smsService.checkVerification(phoneNumber, otpCode);
        if (!verified) {
            throw new VerificationException("Invalid or expired OTP. Please try again or request a new code.");
        }

        // Mark user's phone as verified
        user.setIsPhoneVerified(true);
        userRepository.save(user);

        // Send success notification
        smsService.sendVerificationSuccess(phoneNumber);

        logger.info("✅ Phone verified successfully via Twilio Verify for user: {}", userEmail);
    }

    /**
     * Mask phone number for logging/response (show last 4 digits).
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
