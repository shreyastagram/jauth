package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.VerificationException;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for phone number verification.
 * OTP is generated and verified locally; SMS provider handles delivery only.
 */
@Service
public class PhoneVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // In-memory OTP store for phone verification
    private final Map<String, PhoneOtpEntry> otpStore = new ConcurrentHashMap<>();

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

    /**
     * Send OTP to user's phone number for verification.
     * OTP is generated locally and sent via SMS provider.
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

        // Rate limiting
        PhoneOtpEntry existing = otpStore.get(phoneNumber);
        if (existing != null && existing.createdAt.plusMinutes(rateLimitMinutes).isAfter(LocalDateTime.now())) {
            throw new VerificationException("Too many OTP requests. Please wait before trying again.");
        }

        // Generate OTP locally
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Store for local verification
        otpStore.put(phoneNumber, new PhoneOtpEntry(otp, expiresAt));

        // Send via SMS provider
        boolean sent = smsService.sendOtp(phoneNumber, otp);
        if (!sent) {
            otpStore.remove(phoneNumber);
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
        PhoneOtpEntry otpEntry = otpStore.get(phoneNumber);
        if (otpEntry == null) {
            throw new VerificationException("No pending verification. Please request a new OTP.");
        }

        // Check expiration
        if (otpEntry.expiresAt.isBefore(LocalDateTime.now())) {
            otpStore.remove(phoneNumber);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        // Increment and check attempts
        int currentAttempts = otpEntry.attempts.incrementAndGet();
        if (currentAttempts > maxAttempts) {
            otpStore.remove(phoneNumber);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (!otpEntry.otp.equals(otpCode)) {
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // OTP verified — remove from store
        otpStore.remove(phoneNumber);

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
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        int sizeBefore = otpStore.size();
        otpStore.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        int removed = sizeBefore - otpStore.size();
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

    /**
     * Inner class for storing phone OTP entries.
     */
    private static class PhoneOtpEntry {
        final String otp;
        final LocalDateTime expiresAt;
        final LocalDateTime createdAt;
        final AtomicInteger attempts;

        PhoneOtpEntry(String otp, LocalDateTime expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
            this.attempts = new AtomicInteger(0);
        }
    }
}
