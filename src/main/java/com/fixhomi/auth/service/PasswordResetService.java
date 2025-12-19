package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.PasswordResetToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.PasswordResetTokenRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.EmailService;
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

/**
 * Service for password reset functionality.
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${fixhomi.verification.password-reset.expiration-hours:1}")
    private int tokenExpirationHours;

    @Value("${fixhomi.verification.password-reset.rate-limit-minutes:5}")
    private int rateLimitMinutes;

    @Value("${fixhomi.verification.password-reset.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

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

        // Build reset URL (frontend URL)
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;

        // Send email
        boolean sent = emailService.sendPasswordResetEmail(
                user.getEmail(), user.getFullName(), token, resetUrl);
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
}
