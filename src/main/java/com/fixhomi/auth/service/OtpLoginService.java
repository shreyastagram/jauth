package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import com.fixhomi.auth.service.notification.EmailService;
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
 * Service for OTP-based passwordless login.
 * Supports both phone number OTP login and email OTP login.
 * OTP generation and verification is handled locally.
 */
@Service
public class OtpLoginService {

    private static final Logger logger = LoggerFactory.getLogger(OtpLoginService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // In-memory store for email OTPs
    private final Map<String, EmailOtpEntry> emailOtpStore = new ConcurrentHashMap<>();

    // In-memory store for phone OTPs (local generation + verification)
    private final Map<String, PhoneOtpEntry> phoneOtpStore = new ConcurrentHashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

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

    // ==================== PHONE OTP LOGIN ====================

    /**
     * Send OTP for phone-based login.
     * OTP is generated locally and sent via SMS provider.
     */
    public String sendPhoneLoginOtp(String phoneNumber) {
        // Rate limiting
        PhoneOtpEntry existing = phoneOtpStore.get(phoneNumber);
        if (existing != null && existing.createdAt.plusMinutes(rateLimitMinutes).isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // Find user — return same response whether user exists or not (prevent enumeration)
        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);

        if (user == null || !user.getIsActive()) {
            logger.warn("Phone OTP requested for non-existent or inactive phone: {}", maskPhoneNumber(phoneNumber));
            return maskPhoneNumber(phoneNumber);
        }

        // Generate OTP locally
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Store OTP locally for verification
        phoneOtpStore.put(phoneNumber, new PhoneOtpEntry(otp, expiresAt, user.getId()));

        // Send OTP via SMS provider
        boolean sent = smsService.sendOtp(phoneNumber, otp);
        if (!sent) {
            phoneOtpStore.remove(phoneNumber);
            logger.warn("Failed to send login OTP for phone: {}", maskPhoneNumber(phoneNumber));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("Login OTP sent to phone: {}", maskPhoneNumber(phoneNumber));
        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP and complete phone-based login.
     * OTP is verified locally.
     */
    @Transactional
    public LoginResponse verifyPhoneLoginOtp(String phoneNumber, String otpCode) {
        // Get stored OTP
        PhoneOtpEntry otpEntry = phoneOtpStore.get(phoneNumber);
        if (otpEntry == null) {
            throw new VerificationException("No pending login. Please request a new OTP.");
        }

        // Check expiration
        if (otpEntry.expiresAt.isBefore(LocalDateTime.now())) {
            phoneOtpStore.remove(phoneNumber);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        // Increment and check attempts
        int currentAttempts = otpEntry.attempts.incrementAndGet();
        if (currentAttempts > maxAttempts) {
            phoneOtpStore.remove(phoneNumber);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (!otpEntry.otp.equals(otpCode)) {
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // OTP verified — remove from store
        phoneOtpStore.remove(phoneNumber);

        // Find user
        User user = userRepository.findById(otpEntry.userId)
                .orElseThrow(() -> new AuthenticationException("Invalid phone number or OTP"));

        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Auto-verify phone if not already verified
        if (!user.getIsPhoneVerified()) {
            user.setIsPhoneVerified(true);
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("User logged in via phone OTP: {} (role: {})", user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified()
        );
    }

    // ==================== EMAIL OTP LOGIN ====================

    /**
     * Send OTP for email-based login.
     */
    @Transactional
    public String sendEmailLoginOtp(String email) {
        // Rate limiting first (before user lookup to prevent timing attacks)
        EmailOtpEntry existing = emailOtpStore.get(email.toLowerCase());
        if (existing != null && existing.createdAt.plusMinutes(rateLimitMinutes).isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // Find user — return same response whether user exists or not (prevent enumeration)
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !user.getIsActive()) {
            logger.warn("Email OTP requested for non-existent or inactive email: {}", maskEmail(email));
            return maskEmail(email);
        }

        // Generate new OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Store OTP
        emailOtpStore.put(email.toLowerCase(), new EmailOtpEntry(otp, expiresAt, user.getId()));

        // Send OTP via email
        boolean sent = emailService.sendLoginOtp(email, user.getFullName(), otp);
        if (!sent) {
            logger.warn("Failed to send login OTP to email: {}", maskEmail(email));
        }

        logger.info("Login OTP sent to email: {}", maskEmail(email));
        return maskEmail(email);
    }

    /**
     * Verify OTP and complete email-based login.
     */
    @Transactional
    public LoginResponse verifyEmailLoginOtp(String email, String otpCode) {
        String emailLower = email.toLowerCase();

        EmailOtpEntry otpEntry = emailOtpStore.get(emailLower);
        if (otpEntry == null) {
            throw new VerificationException("No valid OTP found. Please request a new one.");
        }

        if (otpEntry.expiresAt.isBefore(LocalDateTime.now())) {
            emailOtpStore.remove(emailLower);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        int currentAttempts = otpEntry.attempts.incrementAndGet();
        if (currentAttempts > maxAttempts) {
            emailOtpStore.remove(emailLower);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!otpEntry.otp.equals(otpCode)) {
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        emailOtpStore.remove(emailLower);

        User user = userRepository.findById(otpEntry.userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        if (!user.getIsEmailVerified()) {
            user.setIsEmailVerified(true);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("User logged in via email OTP: {} (role: {})", user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified()
        );
    }

    // ==================== HELPER METHODS ====================

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

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "****" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "****" + email.substring(atIndex);
    }

    /**
     * Cleanup expired OTP entries every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();

        int emailBefore = emailOtpStore.size();
        emailOtpStore.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        int removedEmail = emailBefore - emailOtpStore.size();

        int phoneBefore = phoneOtpStore.size();
        phoneOtpStore.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        int removedPhone = phoneBefore - phoneOtpStore.size();

        if (removedEmail > 0) {
            logger.info("Cleaned up {} expired email OTP entries", removedEmail);
        }
        if (removedPhone > 0) {
            logger.info("Cleaned up {} expired phone OTP entries", removedPhone);
        }
    }

    /**
     * Inner class for storing email OTP entries.
     */
    private static class EmailOtpEntry {
        final String otp;
        final LocalDateTime expiresAt;
        final LocalDateTime createdAt;
        final Long userId;
        final AtomicInteger attempts;

        EmailOtpEntry(String otp, LocalDateTime expiresAt, Long userId) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
            this.userId = userId;
            this.attempts = new AtomicInteger(0);
        }
    }

    /**
     * Inner class for storing phone OTP entries with local verification.
     */
    private static class PhoneOtpEntry {
        final String otp;
        final LocalDateTime expiresAt;
        final LocalDateTime createdAt;
        final Long userId;
        final AtomicInteger attempts;

        PhoneOtpEntry(String otp, LocalDateTime expiresAt, Long userId) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
            this.userId = userId;
            this.attempts = new AtomicInteger(0);
        }
    }
}
