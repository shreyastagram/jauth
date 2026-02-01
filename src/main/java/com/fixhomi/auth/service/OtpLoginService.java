package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for OTP-based passwordless login.
 * Supports both phone number OTP login and email OTP login.
 */
@Service
public class OtpLoginService {

    private static final Logger logger = LoggerFactory.getLogger(OtpLoginService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // In-memory store for email OTPs (for production, use Redis or database)
    private final Map<String, EmailOtpEntry> emailOtpStore = new ConcurrentHashMap<>();
    
    // In-memory store for pending phone logins (maps phone -> userId)
    // Needed because Twilio Verify handles OTP storage, we just track who requested it
    private static final ConcurrentHashMap<String, Long> pendingPhoneLogins = new ConcurrentHashMap<>();

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

    // Phone OTP configuration is managed by Twilio Verify API
    // These settings are only used for Email OTP (handled locally)
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

    // ==================== PHONE OTP LOGIN (Twilio Verify) ====================

    /**
     * Send OTP for phone-based login via Twilio Verify API.
     * Twilio handles OTP generation, delivery, expiry, and rate limiting.
     * 
     * @param phoneNumber user's phone number
     * @return masked phone number
     */
    public String sendPhoneLoginOtp(String phoneNumber) {
        // Find user by phone number
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User", "phoneNumber", phoneNumber));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Start verification via Twilio Verify API
        // Twilio handles rate limiting, OTP generation, and expiry internally
        boolean sent = smsService.startVerification(phoneNumber);
        if (!sent) {
            logger.warn("Failed to start login OTP verification for phone: {}", maskPhoneNumber(phoneNumber));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        // Store mapping of phone to user for verification step
        pendingPhoneLogins.put(phoneNumber, user.getId());

        logger.info("📱 Login OTP sent via Twilio Verify to phone: {}", maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP and complete phone-based login via Twilio Verify API.
     * Twilio validates the OTP internally with attempt tracking.
     * 
     * @param phoneNumber user's phone number
     * @param otpCode OTP code to verify
     * @return LoginResponse with JWT tokens
     */
    @Transactional
    public LoginResponse verifyPhoneLoginOtp(String phoneNumber, String otpCode) {
        // Check if there's a pending login for this phone
        Long userId = pendingPhoneLogins.get(phoneNumber);
        if (userId == null) {
            throw new VerificationException("No pending login. Please request a new OTP.");
        }

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Invalid phone number or OTP"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify OTP via Twilio Verify API
        // Twilio handles attempt counting and expiry internally
        boolean verified = smsService.checkVerification(phoneNumber, otpCode);
        if (!verified) {
            throw new VerificationException("Invalid or expired OTP. Please try again or request a new code.");
        }

        // OTP verified - remove from pending
        pendingPhoneLogins.remove(phoneNumber);

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

        logger.info("✅ User logged in via phone OTP (Twilio Verify): {} (role: {})", user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds()
        );
    }

    // ==================== EMAIL OTP LOGIN ====================

    /**
     * Send OTP for email-based login.
     * 
     * @param email user's email
     * @return masked email
     */
    @Transactional
    public String sendEmailLoginOtp(String email) {
        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Rate limiting (check existing OTPs in store)
        EmailOtpEntry existing = emailOtpStore.get(email.toLowerCase());
        if (existing != null && existing.createdAt.plusMinutes(rateLimitMinutes).isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
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
     * 
     * @param email user's email
     * @param otpCode OTP code to verify
     * @return LoginResponse with JWT tokens
     */
    @Transactional
    public LoginResponse verifyEmailLoginOtp(String email, String otpCode) {
        String emailLower = email.toLowerCase();

        // Get stored OTP
        EmailOtpEntry otpEntry = emailOtpStore.get(emailLower);
        if (otpEntry == null) {
            throw new VerificationException("No valid OTP found. Please request a new one.");
        }

        // Check expiration
        if (otpEntry.expiresAt.isBefore(LocalDateTime.now())) {
            emailOtpStore.remove(emailLower);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        // Check attempts
        if (otpEntry.attempts >= maxAttempts) {
            emailOtpStore.remove(emailLower);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Increment attempts
        otpEntry.attempts++;

        // Verify OTP
        if (!otpEntry.otp.equals(otpCode)) {
            int remainingAttempts = maxAttempts - otpEntry.attempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // Remove used OTP
        emailOtpStore.remove(emailLower);

        // Find user
        User user = userRepository.findById(otpEntry.userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Auto-verify email if not already verified
        if (!user.getIsEmailVerified()) {
            user.setIsEmailVerified(true);
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

        logger.info("User logged in via email OTP: {} (role: {})", user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds()
        );
    }

    // ==================== HELPER METHODS ====================

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

    /**
     * Mask email for logging/response.
     */
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
     * Inner class for storing email OTP entries.
     */
    private static class EmailOtpEntry {
        String otp;
        LocalDateTime expiresAt;
        LocalDateTime createdAt;
        Long userId;
        int attempts;

        EmailOtpEntry(String otp, LocalDateTime expiresAt, Long userId) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
            this.userId = userId;
            this.attempts = 0;
        }
    }
}
