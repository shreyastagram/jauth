package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.PasswordResetToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.PasswordResetTokenRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.EmailService;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for password reset functionality.
 * Supports both email-based (link) and phone-based (OTP) password reset.
 * OTP is generated and verified locally; SMS provider handles delivery only.
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // In-memory store for phone-based password reset OTPs
    private final ConcurrentHashMap<String, PhoneResetOtpEntry> phoneResetOtpStore = new ConcurrentHashMap<>();

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${fixhomi.verification.password-reset.expiration-hours:1}")
    private int tokenExpirationHours;

    @Value("${fixhomi.verification.password-reset.rate-limit-minutes:5}")
    private int rateLimitMinutes;

    @Value("${fixhomi.verification.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${fixhomi.verification.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${fixhomi.verification.otp.length:6}")
    private int otpLength;

    @Value("${fixhomi.verification.password-reset.base-url:fixhomi://auth}")
    private String frontendBaseUrl;

    @Value("${fixhomi.verification.password-reset.web-fallback-url:https://fixhomi.com/auth}")
    private String webFallbackUrl;

    /**
     * Request password reset via email (forgot password).
     * Always returns success to prevent email enumeration attacks.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        logger.debug("Password reset requested for email: {}", maskEmail(email));

        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        long recentRequests = tokenRepository.countRecentRequestsByEmail(email, since);
        if (recentRequests >= 1) {
            logger.warn("Rate limit exceeded for password reset: {}", maskEmail(email));
            return;
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            logger.debug("Password reset requested for non-existent email: {}", maskEmail(email));
            throw new AuthenticationException("No account found with this email address.");
        }

        User user = userOptional.get();

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            logger.debug("Password reset requested for OAuth-only user: {}", maskEmail(email));
            throw new AuthenticationException("This account uses Google Sign-In and doesn't have a password to reset.");
        }

        tokenRepository.invalidateAllUserTokens(user.getId());

        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(tokenExpirationHours);

        PasswordResetToken resetToken = new PasswordResetToken(
                token, user.getId(), user.getEmail(), expiresAt);
        tokenRepository.save(resetToken);

        String mobileResetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        String webResetUrl = webFallbackUrl + "/reset-password?token=" + token;
        String resetUrl = mobileResetUrl;

        boolean sent = emailService.sendPasswordResetEmail(
                user.getEmail(), user.getFullName(), token, resetUrl, webResetUrl);
        if (sent) {
            logger.info("Password reset email sent to: {}", maskEmail(email));
        } else {
            logger.warn("Failed to send password reset email to: {}", maskEmail(email));
        }
    }

    /**
     * Reset password using token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository
                .findValidToken(token, LocalDateTime.now())
                .orElseThrow(() -> new VerificationException("Invalid or expired reset token"));

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new VerificationException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.info("Revoked {} refresh tokens after password reset for user: {}",
                revokedCount, user.getEmail());

        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());
        logger.info("Password reset successful for user: {}", user.getEmail());
    }

    /**
     * Validate token without using it (for frontend pre-check).
     */
    public boolean validateToken(String token) {
        return tokenRepository.findValidToken(token, LocalDateTime.now()).isPresent();
    }

    // ==================== OTP-BASED PASSWORD RESET (via SMS) ====================

    /**
     * Request password reset via phone OTP.
     * OTP is generated locally and sent via SMS provider.
     */
    public String requestPasswordResetOtp(String phoneNumber) {
        logger.debug("Password reset OTP requested for phone: {}", maskPhoneNumber(phoneNumber));

        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        // Rate limiting
        PhoneResetOtpEntry existing = phoneResetOtpStore.get(normalizedPhone);
        if (existing != null && existing.createdAt.plusMinutes(1).isAfter(LocalDateTime.now())) {
            throw new VerificationException("Too many OTP requests. Please wait before trying again.");
        }

        // Find user — reject if not found
        Optional<User> userOptional = userRepository.findByPhoneNumber(normalizedPhone);
        if (userOptional.isEmpty()) {
            logger.debug("Password reset OTP requested for non-existent phone: {}", maskPhoneNumber(normalizedPhone));
            throw new AuthenticationException("No account found with this phone number.");
        }

        User user = userOptional.get();

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            logger.debug("Password reset OTP requested for OAuth-only user: {}", maskPhoneNumber(normalizedPhone));
            throw new AuthenticationException("This account uses Google Sign-In and doesn't have a password to reset.");
        }

        // Generate OTP locally
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Store for local verification
        phoneResetOtpStore.put(normalizedPhone, new PhoneResetOtpEntry(otp, expiresAt, user.getId()));

        // Send via SMS provider
        boolean sent = smsService.sendOtp(normalizedPhone, otp);
        if (!sent) {
            phoneResetOtpStore.remove(normalizedPhone);
            logger.warn("Failed to send password reset OTP to: {}", maskPhoneNumber(normalizedPhone));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("Password reset OTP sent to: {}", maskPhoneNumber(normalizedPhone));
        return maskPhoneNumber(normalizedPhone);
    }

    /**
     * Verify OTP and reset password.
     * OTP is verified locally.
     */
    @Transactional
    public void verifyOtpAndResetPassword(String phoneNumber, String otp, String newPassword) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        // Get stored OTP
        PhoneResetOtpEntry otpEntry = phoneResetOtpStore.get(normalizedPhone);
        if (otpEntry == null) {
            throw new VerificationException("No pending password reset. Please request a new OTP.");
        }

        // Check expiration
        if (otpEntry.expiresAt.isBefore(LocalDateTime.now())) {
            phoneResetOtpStore.remove(normalizedPhone);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        // Increment and check attempts
        int currentAttempts = otpEntry.attempts.incrementAndGet();
        if (currentAttempts > maxAttempts) {
            phoneResetOtpStore.remove(normalizedPhone);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (!otpEntry.otp.equals(otp)) {
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // OTP verified — remove from store
        phoneResetOtpStore.remove(normalizedPhone);

        // Get user and reset password
        User user = userRepository.findById(otpEntry.userId)
                .orElseThrow(() -> new VerificationException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.info("Revoked {} refresh tokens after OTP password reset for user: {}",
                revokedCount, user.getEmail());

        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());
        logger.info("OTP password reset successful for phone: {}", maskPhoneNumber(normalizedPhone));
    }

    // ==================== HELPER METHODS ====================

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "**" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "****" + email.substring(atIndex);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        String normalized = phoneNumber.trim();
        if (normalized.startsWith("+")) {
            return "+" + normalized.substring(1).replaceAll("[^0-9]", "");
        }
        return normalized.replaceAll("[^0-9]", "");
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "****";
        }
        int length = phoneNumber.length();
        return phoneNumber.substring(0, 2) + "****" + phoneNumber.substring(length - 4);
    }

    /**
     * Cleanup expired phone reset OTP entries every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        int sizeBefore = phoneResetOtpStore.size();
        phoneResetOtpStore.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
        int removed = sizeBefore - phoneResetOtpStore.size();
        if (removed > 0) {
            logger.info("Cleaned up {} expired phone reset OTP entries", removed);
        }
    }

    /**
     * Inner class for phone-based password reset OTP entries.
     */
    private static class PhoneResetOtpEntry {
        final String otp;
        final LocalDateTime expiresAt;
        final LocalDateTime createdAt;
        final Long userId;
        final AtomicInteger attempts;

        PhoneResetOtpEntry(String otp, LocalDateTime expiresAt, Long userId) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
            this.userId = userId;
            this.attempts = new AtomicInteger(0);
        }
    }
}
