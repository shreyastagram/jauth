package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.AppleEmailOtp;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.AppleEmailOtpRepository;
import com.fixhomi.auth.security.JwtService;
import com.fixhomi.auth.service.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for handling Apple Sign-In email verification via OTP.
 *
 * When Apple does not provide the user's email (phone-only Apple ID or
 * subsequent sign-in after "Hide My Email"), the frontend collects an email
 * and verifies it through this OTP flow before retrying registration.
 *
 * Flow:
 * 1. Frontend calls sendOtp(email, appleUserId) — OTP sent to email
 * 2. User enters OTP in the app
 * 3. Frontend calls verifyOtp(email, appleUserId, otp) — returns a signed verification token
 * 4. Frontend retries Apple auth with the verificationToken in the request
 * 5. AppleAuthService validates the token and uses the verified email
 */
@Service
public class AppleEmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(AppleEmailVerificationService.class);

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RATE_LIMIT_PER_MINUTE = 3;

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private AppleEmailOtpRepository appleEmailOtpRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    /**
     * Send an OTP to the given email for Apple Sign-In email verification.
     * Rate limited to 3 requests per minute per appleUserId.
     *
     * @param email the email address to verify
     * @param appleUserId Apple's stable user identifier
     */
    @Transactional
    public void sendOtp(String email, String appleUserId) {
        if (email == null || email.isBlank()) {
            throw new VerificationException("Email address is required.");
        }
        if (appleUserId == null || appleUserId.isBlank()) {
            throw new VerificationException("Apple user ID is required.");
        }

        logger.info("Apple email OTP requested for appleUserId: {}, email: {}", appleUserId, email);

        // Rate limiting: max 3 OTP requests per minute per appleUserId
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long recentCount = appleEmailOtpRepository.countByAppleUserIdAndCreatedAtAfter(appleUserId, oneMinuteAgo);
        if (recentCount >= RATE_LIMIT_PER_MINUTE) {
            logger.warn("Apple email OTP rate limit exceeded for appleUserId: {}", appleUserId);
            throw new TooManyRequestsException(
                    "Too many verification requests. Please wait 60 seconds before trying again.");
        }

        // Generate 6-digit OTP
        String otp = generateOtp();

        // Send OTP via email FIRST — only save to database if email succeeds
        boolean sent = emailService.sendLoginOtp(email, email.split("@")[0], otp);
        if (!sent) {
            logger.error("Failed to send Apple email OTP to: {}", email);
            throw new VerificationException("Unable to send verification email. Please try again.");
        }

        // Save to database only after email sent successfully
        AppleEmailOtp otpEntity = new AppleEmailOtp(
                appleUserId,
                email,
                otp,
                LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)
        );
        appleEmailOtpRepository.save(otpEntity);

        logger.info("Apple email OTP sent successfully to: {} for appleUserId: {}", email, appleUserId);
    }

    /**
     * Verify an OTP and return a signed verification token.
     * Max 5 attempts per OTP. On success, returns a JWT containing the verified email.
     *
     * @param email the email address
     * @param appleUserId Apple's stable user identifier
     * @param otp the OTP entered by the user
     * @return signed JWT verification token (10 min TTL)
     */
    @Transactional
    public String verifyOtp(String email, String appleUserId, String otp) {
        if (email == null || email.isBlank()) {
            throw new VerificationException("Email address is required.");
        }
        if (appleUserId == null || appleUserId.isBlank()) {
            throw new VerificationException("Apple user ID is required.");
        }
        if (otp == null || otp.isBlank()) {
            throw new VerificationException("Verification code is required.");
        }

        logger.info("Apple email OTP verification attempt for appleUserId: {}, email: {}", appleUserId, email);

        // Find the latest unverified OTP for this apple user + email
        Optional<AppleEmailOtp> otpOpt = appleEmailOtpRepository
                .findTopByAppleUserIdAndEmailAndVerifiedFalseOrderByCreatedAtDesc(appleUserId, email);

        if (otpOpt.isEmpty()) {
            logger.warn("No pending OTP found for appleUserId: {}, email: {}", appleUserId, email);
            throw new VerificationException("No pending verification code found. Please request a new one.");
        }

        AppleEmailOtp otpEntity = otpOpt.get();

        // Check expiry
        if (otpEntity.isExpired()) {
            logger.warn("Expired OTP for appleUserId: {}, email: {}", appleUserId, email);
            throw new VerificationException("Verification code has expired. Please request a new one.");
        }

        // Check max attempts
        if (otpEntity.getAttempts() >= MAX_ATTEMPTS) {
            logger.warn("Max OTP attempts exceeded for appleUserId: {}, email: {}", appleUserId, email);
            throw new VerificationException(
                    "Too many incorrect attempts. Please request a new verification code.");
        }

        // Increment attempts
        otpEntity.incrementAttempts();
        appleEmailOtpRepository.save(otpEntity);

        // Verify OTP
        if (!otp.equals(otpEntity.getOtp())) {
            int remaining = MAX_ATTEMPTS - otpEntity.getAttempts();
            logger.warn("Invalid OTP for appleUserId: {}, email: {} — {} attempts remaining",
                    appleUserId, email, remaining);
            throw new VerificationException(
                    "Incorrect verification code. " + remaining + " attempt(s) remaining.");
        }

        // Mark as verified
        otpEntity.setVerified(true);
        appleEmailOtpRepository.save(otpEntity);

        // Clean up only the verified OTP record
        appleEmailOtpRepository.delete(otpEntity);

        // Generate signed verification token
        String verificationToken = jwtService.generateVerificationToken(appleUserId, email);

        logger.info("Apple email OTP verified successfully for appleUserId: {}, email: {}", appleUserId, email);

        return verificationToken;
    }

    /**
     * Generate a cryptographically secure 6-digit OTP.
     */
    private String generateOtp() {
        int otp = secureRandom.nextInt(900000) + 100000; // 100000–999999
        return String.valueOf(otp);
    }
}
