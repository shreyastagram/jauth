package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.EmailOtp;
import com.fixhomi.auth.entity.PhoneOtp;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.EmailOtpRepository;
import com.fixhomi.auth.repository.PhoneOtpRepository;
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

/**
 * Service for OTP-based passwordless login.
 * Supports both phone number OTP login and email OTP login.
 * OTP generation and verification is handled locally.
 */
@Service
public class OtpLoginService {

    private static final Logger logger = LoggerFactory.getLogger(OtpLoginService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private EmailOtpRepository emailOtpRepository;

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
    @Transactional
    public String sendPhoneLoginOtp(String phoneNumber) {
        // Rate limiting
        long recentRequests = phoneOtpRepository.countRecentOtpRequests(phoneNumber, LocalDateTime.now().minusMinutes(rateLimitMinutes));
        if (recentRequests >= rateLimitMaxRequests) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // Find user — reject if not registered
        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);

        if (user == null) {
            logger.warn("Phone OTP requested for non-existent phone: {}", maskPhoneNumber(phoneNumber));
            throw new AuthenticationException("No account found with this phone number. Please sign up first.");
        }
        if (!user.getIsActive()) {
            logger.warn("Phone OTP requested for inactive phone: {}", maskPhoneNumber(phoneNumber));
            throw new AuthenticationException("Your account has been disabled. Please contact support.");
        }

        // Invalidate old OTPs
        phoneOtpRepository.invalidateAllUserOtps(user.getId(), phoneNumber);

        // Generate OTP locally
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Save to database
        PhoneOtp phoneOtp = new PhoneOtp(user.getId(), phoneNumber, otp, expiresAt);
        phoneOtpRepository.save(phoneOtp);

        // Send OTP via SMS provider
        boolean sent = smsService.sendOtp(phoneNumber, otp);
        if (!sent) {
            phoneOtp.setVerified(true); // invalidate the OTP
            phoneOtpRepository.save(phoneOtp);
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
        PhoneOtp otpEntry = phoneOtpRepository.findLatestValidOtpByPhone(phoneNumber, LocalDateTime.now())
                .orElse(null);
        if (otpEntry == null) {
            throw new VerificationException("No pending login. Please request a new OTP.");
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

        // Find user
        User user = userRepository.findById(otpEntry.getUserId())
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
        String emailLower = email.toLowerCase();

        // Rate limiting first (before user lookup to prevent timing attacks)
        long recentRequests = emailOtpRepository.countRecentOtpRequests(emailLower, LocalDateTime.now().minusMinutes(rateLimitMinutes));
        if (recentRequests >= rateLimitMaxRequests) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // Find user — reject if not registered
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            logger.warn("Email OTP requested for non-existent email: {}", maskEmail(email));
            throw new AuthenticationException("No account found with this email. Please sign up first.");
        }
        if (!user.getIsActive()) {
            logger.warn("Email OTP requested for inactive email: {}", maskEmail(email));
            throw new AuthenticationException("Your account has been disabled. Please contact support.");
        }

        // Invalidate old OTPs
        emailOtpRepository.invalidateAllUserOtps(user.getId(), emailLower);

        // Generate new OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Save to database
        EmailOtp emailOtp = new EmailOtp(user.getId(), emailLower, otp, expiresAt);
        emailOtpRepository.save(emailOtp);

        // Send OTP via email
        boolean sent = emailService.sendLoginOtp(email, user.getFullName(), otp);
        if (!sent) {
            emailOtp.setVerified(true);
            emailOtpRepository.save(emailOtp);
            logger.warn("Failed to send login OTP to email: {}", maskEmail(email));
            throw new VerificationException("Failed to send OTP. Please try again.");
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

        EmailOtp otpEntry = emailOtpRepository.findLatestValidOtpByEmail(emailLower, LocalDateTime.now())
                .orElse(null);
        if (otpEntry == null) {
            throw new VerificationException("No valid OTP found. Please request a new one.");
        }

        if (otpEntry.isExpired()) {
            otpEntry.setVerified(true);
            emailOtpRepository.save(otpEntry);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        otpEntry.incrementAttempts();
        int currentAttempts = otpEntry.getAttempts();
        if (currentAttempts > maxAttempts) {
            otpEntry.setVerified(true);
            emailOtpRepository.save(otpEntry);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!java.security.MessageDigest.isEqual(otpEntry.getOtp().getBytes(java.nio.charset.StandardCharsets.UTF_8), otpCode.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            emailOtpRepository.save(otpEntry);
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // OTP verified — mark as verified
        otpEntry.setVerified(true);
        emailOtpRepository.save(otpEntry);

        User user = userRepository.findById(otpEntry.getUserId())
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
    @Transactional
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();

        int removedEmail = emailOtpRepository.deleteExpiredOtps(now);
        int removedPhone = phoneOtpRepository.deleteExpiredOtps(now);

        if (removedEmail > 0) {
            logger.info("Cleaned up {} expired email OTP entries", removedEmail);
        }
        if (removedPhone > 0) {
            logger.info("Cleaned up {} expired phone OTP entries", removedPhone);
        }
    }
}
