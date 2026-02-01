package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.PasswordResetToken;
import com.fixhomi.auth.entity.User;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for password reset functionality.
 * Supports both email-based (link) and phone-based (OTP) password reset.
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Temporary mapping of phone numbers to user IDs for password reset flow
    // This is needed because Twilio Verify handles OTP storage, we just need to track who requested it
    private static final ConcurrentHashMap<String, Long> pendingPasswordResets = new ConcurrentHashMap<>();

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

    // OTP configuration is now handled by Twilio Verify API
    // Twilio manages OTP generation, expiration, and attempt limits automatically

    @Value("${fixhomi.verification.password-reset.base-url:fixhomi://auth}")
    private String frontendBaseUrl;
    
    @Value("${fixhomi.verification.password-reset.web-fallback-url:https://fixhomi.com/auth}")
    private String webFallbackUrl;

    /**
     * Request password reset (forgot password).
     * Always returns success to prevent email enumeration attacks.
     *
     * @param email user's email address
     */
    @Transactional
    public void requestPasswordReset(String email) {
        logger.debug("Password reset requested for email: {}", maskEmail(email));

        // Rate limiting by email (before user lookup to prevent enumeration)
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        long recentRequests = tokenRepository.countRecentRequestsByEmail(email, since);
        if (recentRequests >= 1) {
            // Don't reveal rate limit to prevent enumeration
            logger.warn("Rate limit exceeded for password reset: {}", maskEmail(email));
            return;
        }

        // Find user (silently fail if not found to prevent enumeration)
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            logger.debug("Password reset requested for non-existent email: {}", maskEmail(email));
            return; // Silent fail
        }

        User user = userOptional.get();

        // Don't allow password reset for OAuth-only users (no password set)
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            logger.debug("Password reset requested for OAuth-only user: {}", maskEmail(email));
            return; // Silent fail
        }

        // Invalidate any existing tokens
        tokenRepository.invalidateAllUserTokens(user.getId());

        // Generate new token
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(tokenExpirationHours);

        PasswordResetToken resetToken = new PasswordResetToken(
                token, user.getId(), user.getEmail(), expiresAt);
        tokenRepository.save(resetToken);

        // Build reset URLs
        // Mobile deep link (works on iOS/Android when app is installed)
        String mobileResetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        // Web fallback URL (for browsers or when app not installed)
        String webResetUrl = webFallbackUrl + "/reset-password?token=" + token;
        
        // Primary URL for mobile apps - use deep link
        String resetUrl = mobileResetUrl;

        // Send email with both URLs
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
     *
     * @param token the reset token
     * @param newPassword the new password
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository
                .findValidToken(token, LocalDateTime.now())
                .orElseThrow(() -> new VerificationException("Invalid or expired reset token"));

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Update user's password
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new VerificationException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens for security
        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.info("Revoked {} refresh tokens after password reset for user: {}", 
                revokedCount, user.getEmail());

        // Send notification
        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());

        logger.info("Password reset successful for user: {}", user.getEmail());
    }

    /**
     * Validate token without using it (for frontend pre-check).
     *
     * @param token the reset token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        return tokenRepository.findValidToken(token, LocalDateTime.now()).isPresent();
    }

    /**
     * Generate a secure random token.
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Mask email for logging.
     */
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

    // ==================== OTP-BASED PASSWORD RESET (Twilio Verify) ====================

    /**
     * Request password reset via phone OTP using Twilio Verify API.
     * Twilio handles OTP generation, delivery, and expiry management.
     * 
     * @param phoneNumber user's phone number
     * @return masked phone number
     * @throws VerificationException if no account found with this phone
     */
    public String requestPasswordResetOtp(String phoneNumber) {
        logger.debug("Password reset OTP requested for phone: {}", maskPhoneNumber(phoneNumber));

        // Normalize phone number (remove spaces, dashes)
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        // Find user by phone number
        Optional<User> userOptional = userRepository.findByPhoneNumber(normalizedPhone);
        if (userOptional.isEmpty()) {
            logger.debug("Password reset OTP requested for non-existent phone: {}", maskPhoneNumber(normalizedPhone));
            throw new VerificationException("No account found with this phone number.");
        }

        User user = userOptional.get();

        // Don't allow password reset for OAuth-only users (no password set)
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            logger.debug("Password reset OTP requested for OAuth-only user: {}", maskPhoneNumber(normalizedPhone));
            throw new VerificationException("This account uses Google Sign-In. No password to reset.");
        }

        // Start verification via Twilio Verify API
        // Twilio handles rate limiting, OTP generation, and expiry internally
        boolean sent = smsService.startVerification(normalizedPhone);
        if (!sent) {
            logger.warn("Failed to send password reset OTP to: {}", maskPhoneNumber(normalizedPhone));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        // Store mapping of phone to user for verification step
        pendingPasswordResets.put(normalizedPhone, user.getId());

        logger.info("📱 Password reset OTP sent via Twilio Verify to: {}", maskPhoneNumber(normalizedPhone));
        return maskPhoneNumber(normalizedPhone);
    }

    /**
     * Verify OTP and reset password using Twilio Verify API.
     * Twilio validates the OTP internally.
     * 
     * @param phoneNumber user's phone number
     * @param otp the OTP code entered by user
     * @param newPassword the new password
     * @throws VerificationException if OTP is invalid or expired
     */
    @Transactional
    public void verifyOtpAndResetPassword(String phoneNumber, String otp, String newPassword) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        
        logger.debug("Password reset OTP verification for phone: {}", maskPhoneNumber(normalizedPhone));

        // Check if there's a pending password reset for this phone
        Long userId = pendingPasswordResets.get(normalizedPhone);
        if (userId == null) {
            throw new VerificationException("No pending password reset. Please request a new OTP.");
        }

        // Verify OTP via Twilio Verify API
        // Twilio handles attempt counting and expiry internally
        boolean verified = smsService.checkVerification(normalizedPhone, otp);
        if (!verified) {
            throw new VerificationException("Invalid or expired OTP. Please try again or request a new code.");
        }

        // OTP verified - remove from pending
        pendingPasswordResets.remove(normalizedPhone);

        // Get user and reset password
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new VerificationException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens for security
        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.info("Revoked {} refresh tokens after OTP password reset for user: {}", 
                revokedCount, user.getEmail());

        // Send notification
        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());

        logger.info("✅ OTP password reset successful via Twilio Verify for phone: {}", maskPhoneNumber(normalizedPhone));
    }

    /**
     * Normalize phone number (remove spaces, dashes, etc.)
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        // Remove all non-digit characters except leading +
        String normalized = phoneNumber.trim();
        if (normalized.startsWith("+")) {
            return "+" + normalized.substring(1).replaceAll("[^0-9]", "");
        }
        return normalized.replaceAll("[^0-9]", "");
    }

    /**
     * Mask phone number for logging/display.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "****";
        }
        int length = phoneNumber.length();
        return phoneNumber.substring(0, 2) + "****" + phoneNumber.substring(length - 4);
    }
}
