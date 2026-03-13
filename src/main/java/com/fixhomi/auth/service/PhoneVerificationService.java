package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.PhoneOtp;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.PhoneOtpRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service for phone number verification.
 * OTP is generated and verified locally; SMS provider handles delivery only.
 */
@Service
public class PhoneVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private SmsService smsService;

    @Value("${fixhomi.verification.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${fixhomi.verification.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${fixhomi.verification.otp.length:6}")
    private int otpLength;

    @Value("${fixhomi.verification.otp.rate-limit-minutes:5}")
    private int rateLimitMinutes;

    @Value("${fixhomi.verification.otp.rate-limit-max-requests:3}")
    private int rateLimitMaxRequests;

    @Value("${fixhomi.notification.sms.msg91.verification-template-id:}")
    private String verificationTemplateId;

    /**
     * Send OTP to user's phone number for verification.
     * OTP is generated locally and sent via SMS provider.
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
        long recentRequests = phoneOtpRepository.countRecentOtpRequests(phoneNumber, LocalDateTime.now().minusMinutes(rateLimitMinutes));
        if (recentRequests >= rateLimitMaxRequests) {
            throw new VerificationException("Too many OTP requests. Please wait before trying again.");
        }

        // Invalidate old OTPs
        phoneOtpRepository.invalidateAllUserOtps(user.getId(), phoneNumber);

        // Generate OTP locally
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Save to database
        PhoneOtp phoneOtp = new PhoneOtp(user.getId(), phoneNumber, otp, expiresAt);
        phoneOtpRepository.save(phoneOtp);

        // Send via SMS provider using verification template
        boolean sent = smsService.sendOtp(phoneNumber, otp, verificationTemplateId);
        if (!sent) {
            phoneOtp.setVerified(true); // invalidate the OTP
            phoneOtpRepository.save(phoneOtp);
            logger.warn("Failed to send phone verification OTP for: {}", maskPhoneNumber(phoneNumber));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("Phone verification OTP sent for user: {} (phone: {})",
                userEmail, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP code for phone verification.
     * OTP is verified locally.
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

        // Get stored OTP
        PhoneOtp otpEntry = phoneOtpRepository.findLatestValidOtpByPhone(phoneNumber, LocalDateTime.now())
                .orElse(null);
        if (otpEntry == null) {
            throw new VerificationException("No pending verification. Please request a new OTP.");
        }

        // Check expiration
        if (otpEntry.isExpired()) {
            otpEntry.setVerified(true);
            phoneOtpRepository.save(otpEntry);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        // Increment and check attempts
        otpEntry.incrementAttempts();
        int currentAttempts = otpEntry.getAttempts();
        if (currentAttempts > maxAttempts) {
            otpEntry.setVerified(true);
            phoneOtpRepository.save(otpEntry);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (!java.security.MessageDigest.isEqual(otpEntry.getOtp().getBytes(java.nio.charset.StandardCharsets.UTF_8), otpCode.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            phoneOtpRepository.save(otpEntry);
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // OTP verified — mark as verified
        otpEntry.setVerified(true);
        phoneOtpRepository.save(otpEntry);

        // Mark user's phone as verified
        user.setIsPhoneVerified(true);
        userRepository.save(user);

        // Send success notification
        smsService.sendVerificationSuccess(phoneNumber);

        logger.info("Phone verified successfully for user: {}", userEmail);
    }

    /**
     * Cleanup expired OTP entries every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupExpiredEntries() {
        int removed = phoneOtpRepository.deleteExpiredOtps(LocalDateTime.now());
        if (removed > 0) {
            logger.info("Cleaned up {} expired phone verification OTP entries", removed);
        }
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
