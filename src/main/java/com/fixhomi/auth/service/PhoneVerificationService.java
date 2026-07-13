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
    public String sendOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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

        logger.info("Phone verification OTP sent for user: userId={} (phone: {})",
                userId, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify OTP code for phone verification.
     * OTP is verified locally.
     */
    @Transactional
    public void verifyOtp(Long userId, String otpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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

        // Commit-time cross-user re-check (mirrors the change flow): if another
        // active account verified this number in the meantime, do not create a
        // second verified holder. This user is necessarily unverified here, so
        // any verified holder is a different account.
        if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(phoneNumber)) {
            throw new VerificationException("This phone number is already in use by another account");
        }

        // Mark user's phone as verified
        user.setIsPhoneVerified(true);
        userRepository.save(user);

        // Send success notification
        smsService.sendVerificationSuccess(phoneNumber);

        logger.info("Phone verified successfully for user: userId={}", userId);
    }

    // ==================== VERIFY-THEN-REPLACE PHONE CHANGE ====================

    /**
     * Start a phone change: send an OTP to the NEW number. The account's current
     * number is NOT touched — it stays active and verified until the new number
     * passes OTP verification. Also serves phone-less accounts adding a first
     * number, and re-verification of a stored-but-unverified number.
     *
     * @return masked new phone number for display
     */
    @Transactional
    public String sendChangeOtp(Long userId, String newPhoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String normalized = User.normalizePhoneNumber(newPhoneNumber);
        if (normalized == null || normalized.isBlank()) {
            throw new VerificationException("Please enter a valid phone number");
        }

        if (normalized.equals(user.getPhoneNumber()) && Boolean.TRUE.equals(user.getIsPhoneVerified())) {
            throw new VerificationException("This is already your verified phone number");
        }

        // Another active user holds this number VERIFIED → cannot claim it
        if (!normalized.equals(user.getPhoneNumber())
                && userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(normalized)) {
            throw new VerificationException("This phone number is already in use by another account");
        }

        // Rate limits: per target number (shared with all OTP flows) AND per user
        // across numbers (blocks OTP-spraying many numbers from one account)
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        if (phoneOtpRepository.countRecentOtpRequests(normalized, since) >= rateLimitMaxRequests
                || phoneOtpRepository.countRecentOtpRequestsByUser(userId, since) >= rateLimitMaxRequests) {
            throw new VerificationException("Too many OTP requests. Please wait before trying again.");
        }

        // Invalidate this user's previous OTPs for this number
        phoneOtpRepository.invalidateAllUserOtps(userId, normalized);

        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);
        PhoneOtp phoneOtp = new PhoneOtp(userId, normalized, otp, expiresAt);
        phoneOtpRepository.save(phoneOtp);

        boolean sent = smsService.sendOtp(normalized, otp, verificationTemplateId);
        if (!sent) {
            phoneOtp.setVerified(true); // invalidate the OTP
            phoneOtpRepository.save(phoneOtp);
            logger.warn("Failed to send phone-change OTP for user: userId={} (phone: {})",
                    userId, maskPhoneNumber(normalized));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("Phone-change OTP sent for user: userId={} (new phone: {})",
                userId, maskPhoneNumber(normalized));
        return maskPhoneNumber(normalized);
    }

    /**
     * Complete a phone change: verify the OTP sent to the NEW number and, only
     * on success, atomically replace the account's phoneNumber and mark it
     * verified — one transaction, so there is never a stored-but-unverified
     * number and never a verified flag describing a different number.
     */
    @Transactional
    public void verifyChangeOtp(Long userId, String newPhoneNumber, String otpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String normalized = User.normalizePhoneNumber(newPhoneNumber);
        if (normalized == null || normalized.isBlank()) {
            throw new VerificationException("Please enter a valid phone number");
        }

        // OTP lookup is scoped to THIS user AND this number — an OTP issued to
        // any other user or number can never complete this change.
        PhoneOtp otpEntry = phoneOtpRepository.findLatestValidOtp(userId, normalized, LocalDateTime.now())
                .orElse(null);
        if (otpEntry == null) {
            throw new VerificationException("No pending verification. Please request a new OTP.");
        }

        if (otpEntry.isExpired()) {
            otpEntry.setVerified(true);
            phoneOtpRepository.save(otpEntry);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        otpEntry.incrementAttempts();
        int currentAttempts = otpEntry.getAttempts();
        if (currentAttempts > maxAttempts) {
            otpEntry.setVerified(true);
            phoneOtpRepository.save(otpEntry);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!java.security.MessageDigest.isEqual(
                otpEntry.getOtp().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                otpCode.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            phoneOtpRepository.save(otpEntry);
            int remainingAttempts = maxAttempts - currentAttempts;
            throw new VerificationException("Invalid OTP. " + remainingAttempts + " attempt(s) remaining.");
        }

        // Re-check uniqueness at commit time (someone else may have verified this
        // number between send and verify)
        final Long currentUserId = user.getId();
        User otherHolder = userRepository.findByPhoneNumberAndIsActiveTrue(normalized)
                .filter(other -> !other.getId().equals(currentUserId))
                .orElse(null);
        if (otherHolder != null) {
            if (Boolean.TRUE.equals(otherHolder.getIsPhoneVerified())) {
                otpEntry.setVerified(true);
                phoneOtpRepository.save(otpEntry);
                throw new VerificationException("This phone number is already in use by another account");
            }
            // Unverified squatter — clear so this user can claim the number
            logger.info("Clearing unverified phone {} from user {} for phone change",
                    maskPhoneNumber(normalized), otherHolder.getId());
            otherHolder.setPhoneNumber(null);
            otherHolder.setIsPhoneVerified(false);
            userRepository.save(otherHolder);
        }

        otpEntry.setVerified(true);
        phoneOtpRepository.save(otpEntry);

        // Atomic commit: number + verified flag change together
        String previousPhone = user.getPhoneNumber();
        user.setPhoneNumber(normalized);
        user.setIsPhoneVerified(true);
        userRepository.save(user);

        smsService.sendVerificationSuccess(normalized);

        logger.info("Phone changed and verified for user: userId={} ({} -> {})",
                userId, maskPhoneNumber(previousPhone), maskPhoneNumber(normalized));
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
