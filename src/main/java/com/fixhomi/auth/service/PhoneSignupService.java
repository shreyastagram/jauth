package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.PhoneSignupOtp;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.PhoneSignupOtpRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Account-creation flow for phone-only USERS. Distinct from {@link OtpLoginService},
 * which only handles login for users that already exist. A {@link User} row is created
 * <strong>only on OTP verify</strong> (never on send) so no orphan accounts result from
 * abandoned signups. Hard-coded to {@link Role#USER}; providers are NOT in scope and
 * cannot be created via this path.
 *
 * <p>Phone-only users persist with {@code email = NULL} (never {@code ""}).
 */
@Service
public class PhoneSignupService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneSignupService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhoneSignupOtpRepository phoneSignupOtpRepository;

    @Autowired
    private SmsService smsService;

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

    /**
     * Generate + dispatch a signup OTP for a phone number. The full name is captured
     * now and carried through verify-time so the input flow is single-step from the
     * client's perspective. No user row is created at this stage.
     */
    @Transactional
    public String sendPhoneSignupOtp(String rawPhoneNumber, String fullName) {
        String phoneNumber = User.normalizePhoneNumber(rawPhoneNumber);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new VerificationException("Invalid phone number.");
        }
        String trimmedName = fullName == null ? "" : fullName.trim();
        if (trimmedName.isEmpty()) {
            throw new VerificationException("Full name is required.");
        }

        long recentRequests = phoneSignupOtpRepository.countRecentOtpRequests(
                phoneNumber, LocalDateTime.now().minusMinutes(rateLimitMinutes));
        if (recentRequests >= rateLimitMaxRequests) {
            throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");
        }

        // If a verified+active account (user OR provider) already owns this phone, do not
        // start a new signup. Force them to log in via the existing phone-OTP login flow.
        if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(phoneNumber)) {
            throw new DuplicateResourceException("User", "phoneNumber", rawPhoneNumber);
        }

        // Reclaim an unverified-phone row from a previous abandoned signup so the new
        // attempt can ultimately claim this number on verify. Mirrors AuthService.register.
        userRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber).ifPresent(oldUser -> {
            if (!Boolean.TRUE.equals(oldUser.getIsPhoneVerified())) {
                logger.info("Clearing unverified phone {} from user {} for new signup",
                        oldUser.getId(), oldUser.getId());
                oldUser.setPhoneNumber(null);
                oldUser.setIsPhoneVerified(false);
                userRepository.save(oldUser);
            }
        });

        phoneSignupOtpRepository.invalidateAllForPhone(phoneNumber);

        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);
        PhoneSignupOtp entry = new PhoneSignupOtp(phoneNumber, otp, trimmedName, expiresAt);
        entry = phoneSignupOtpRepository.save(entry);

        boolean sent = smsService.sendOtp(rawPhoneNumber, otp);
        if (!sent) {
            entry.setVerified(true);
            phoneSignupOtpRepository.save(entry);
            logger.warn("Failed to send signup OTP for phone: {}", maskPhoneNumber(phoneNumber));
            throw new VerificationException("Failed to send OTP. Please try again.");
        }

        logger.info("Signup OTP sent to phone: {}", maskPhoneNumber(phoneNumber));
        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Verify the signup OTP and create the {@link User} row. Role is hard-coded to
     * {@link Role#USER}; email persists as {@code NULL}. Returns the standard login
     * envelope (access + refresh tokens) so the client can sign in immediately.
     */
    @Transactional(noRollbackFor = { VerificationException.class })
    public LoginResponse verifyPhoneSignupOtp(String rawPhoneNumber, String otpCode) {
        String phoneNumber = User.normalizePhoneNumber(rawPhoneNumber);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new VerificationException("Invalid phone number.");
        }

        PhoneSignupOtp entry = phoneSignupOtpRepository
                .findLatestValidOtpByPhone(phoneNumber, LocalDateTime.now())
                .orElse(null);
        if (entry == null) {
            throw new VerificationException("No pending signup. Please request a new OTP.");
        }

        if (entry.isExpired()) {
            entry.setVerified(true);
            phoneSignupOtpRepository.save(entry);
            throw new VerificationException("OTP has expired. Please request a new one.");
        }

        entry.incrementAttempts();
        if (entry.getAttempts() > maxAttempts) {
            entry.setVerified(true);
            phoneSignupOtpRepository.save(entry);
            throw new VerificationException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!MessageDigest.isEqual(
                entry.getOtp().getBytes(StandardCharsets.UTF_8),
                otpCode.getBytes(StandardCharsets.UTF_8))) {
            phoneSignupOtpRepository.save(entry);
            throw new VerificationException("Invalid OTP. Please check and try again.");
        }

        entry.setVerified(true);
        phoneSignupOtpRepository.save(entry);

        // Re-check at verify-time in case a parallel signup verified first.
        if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(phoneNumber)) {
            throw new DuplicateResourceException("User", "phoneNumber", rawPhoneNumber);
        }
        userRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber).ifPresent(oldUser -> {
            if (!Boolean.TRUE.equals(oldUser.getIsPhoneVerified())) {
                oldUser.setPhoneNumber(null);
                oldUser.setIsPhoneVerified(false);
                userRepository.save(oldUser);
            }
        });

        // Phone-only USER. Email stays NULL. No password (login is via phone OTP).
        User user = new User();
        user.setEmail(null);
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(null);
        user.setFullName(entry.getFullName());
        user.setRole(Role.USER);
        user.setIsActive(true);
        user.setIsEmailVerified(false);
        user.setIsPhoneVerified(true);
        user.setLastLoginAt(LocalDateTime.now());

        user = userRepository.save(user);

        logger.info("Phone-only USER created via signup: userId={} phone={}",
                user.getId(), maskPhoneNumber(phoneNumber));

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified(),
                user.getIsEmailVerified()
        );
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder(otpLength);
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

    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupExpiredEntries() {
        int removed = phoneSignupOtpRepository.deleteExpiredOtps(LocalDateTime.now());
        if (removed > 0) {
            logger.info("Cleaned up {} expired phone-signup OTP entries", removed);
        }
    }
}
