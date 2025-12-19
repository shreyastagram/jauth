package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.PhoneOtp;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.PhoneOtpRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service for phone number verification via OTP.
 */
@Service
public class PhoneVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmsService smsService;

    @Value("${fixhomi.verification.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${fixhomi.verification.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${fixhomi.verification.otp.length:6}")
    private int otpLength;

    @Value("${fixhomi.verification.otp.rate-limit-minutes:1}")
    private int rateLimitMinutes;

    @Value("${fixhomi.verification.otp.rate-limit-max-requests:3}")
    private int rateLimitMaxRequests;

    /**
     * Send OTP to user's phone number.
     *
     * @param userEmail user's email (from JWT)
     * @return masked phone number
     */
    @Transactional
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

        // Rate limiting
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        long recentRequests = phoneOtpRepository.countRecentOtpRequests(phoneNumber, since);
        if (recentRequests >= rateLimitMaxRequests) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // Invalidate any existing OTPs
        phoneOtpRepository.invalidateAllUserOtps(user.getId(), phoneNumber);

        // Generate new OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        PhoneOtp phoneOtp = new PhoneOtp(user.getId(), phoneNumber, otp, expiresAt);
        phoneOtpRepository.save(phoneOtp);

        // Send OTP via SMS
        boolean sent = smsService.sendOtp(phoneNumber, otp);
        if (!sent) {
            logger.warn("Failed to send OTP to phone: {}", maskPhoneNumber(phoneNumber));
        }

        logger.info("OTP sent to user: {} (phone: {})", userEmail, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP code.
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

        PhoneOtp phoneOtp = phoneOtpRepository.findLatestValidOtp(
                user.getId(), phoneNumber, LocalDateTime.now())
                .orElseThrow(() -> new VerificationException("No valid OTP found. Please request a new one."));

        // Check attempts
        if (phoneOtp.getAttempts() >= maxAttempts) {
            phoneOtp.setVerified(true); // Invalidate
            phoneOtpRepository.save(phoneOtp);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Increment attempts
        phoneOtp.incrementAttempts();

        // Verify OTP
        if (!phoneOtp.getOtp().equals(otpCode)) {
            phoneOtpRepository.save(phoneOtp);
            int remainingAttempts = maxAttempts - phoneOtp.getAttempts();
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // Mark OTP as verified
        phoneOtp.setVerified(true);
        phoneOtpRepository.save(phoneOtp);

        // Mark user's phone as verified
        user.setIsPhoneVerified(true);
        userRepository.save(user);

        // Send success notification
        smsService.sendVerificationSuccess(phoneNumber);

        logger.info("Phone verified successfully for user: {}", userEmail);
    }

    /**
     * Generate a random numeric OTP.
     */
    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
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
