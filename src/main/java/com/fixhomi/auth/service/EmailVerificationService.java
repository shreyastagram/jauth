package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.EmailVerificationToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.EmailVerificationTokenRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service for email verification via token link.
 */
@Service
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Value("${fixhomi.verification.email.expiration-hours:24}")
    private int tokenExpirationHours;

    @Value("${fixhomi.verification.email.rate-limit-minutes:5}")
    private int rateLimitMinutes;

    @Value("${fixhomi.verification.email.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Send email verification link to user.
     *
     * @param userEmail user's email (from JWT)
     * @return masked email address
     */
    @Transactional
    public String sendVerificationEmail(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        if (user.getIsEmailVerified()) {
            throw new VerificationException("Email is already verified");
        }

        // Rate limiting
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        long recentRequests = tokenRepository.countRecentTokenRequests(user.getId(), since);
        if (recentRequests >= 1) {
            throw new TooManyRequestsException("Please wait before requesting another verification email.");
        }

        // Invalidate any existing tokens
        tokenRepository.invalidateAllUserTokens(user.getId());

        // Generate new token
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(tokenExpirationHours);

        EmailVerificationToken verificationToken = new EmailVerificationToken(
                token, user.getId(), user.getEmail(), expiresAt);
        tokenRepository.save(verificationToken);

        // Build verification URL
        String verificationUrl = baseUrl + "/api/auth/email/verify?token=" + token;

        // Send email
        boolean sent = emailService.sendEmailVerification(
                user.getEmail(), user.getFullName(), token, verificationUrl);
        if (!sent) {
            logger.warn("Failed to send verification email to: {}", maskEmail(user.getEmail()));
        }

        logger.info("Verification email sent to user: {}", maskEmail(user.getEmail()));

        return maskEmail(user.getEmail());
    }

    /**
     * Verify email using token from verification link.
     *
     * @param token the verification token
     * @return the verified email address
     */
    @Transactional
    public String verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository
                .findByTokenAndVerifiedFalse(token)
                .orElseThrow(() -> new VerificationException("Invalid or expired verification token"));

        if (verificationToken.isExpired()) {
            throw new VerificationException("Verification token has expired. Please request a new one.");
        }

        // Mark token as verified
        verificationToken.setVerified(true);
        tokenRepository.save(verificationToken);

        // Mark user's email as verified
        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", verificationToken.getUserId()));

        user.setIsEmailVerified(true);
        userRepository.save(user);

        logger.info("Email verified successfully for user: {}", user.getEmail());

        return user.getEmail();
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
     * Mask email for logging/response.
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
